package com.foundation.sync.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncMode;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTriggerMode;
import com.foundation.sync.domain.DeleteProtectionPolicy;
import com.foundation.sync.domain.DeleteProtectionVerdict;
import com.foundation.sync.domain.DiffEntry;
import com.foundation.sync.domain.DiffPreviewService;
import com.foundation.sync.domain.DiffResult;
import com.foundation.sync.domain.FieldMapper;
import com.foundation.sync.domain.FieldMapping;
import com.foundation.sync.domain.IdempotentUpserter;
import com.foundation.sync.domain.LandedChange;
import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.domain.MappedRecord;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.domain.SyncIdPrefix;
import com.foundation.sync.domain.SyncLog;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.domain.SyncTask;
import com.foundation.sync.domain.UpsertOutcome;
import com.foundation.sync.infrastructure.AdapterRegistry;
import com.foundation.sync.infrastructure.SyncConflictRepository;
import com.foundation.sync.infrastructure.SyncEventOutbox;
import com.foundation.sync.infrastructure.SyncFieldMappingRepository;
import com.foundation.sync.infrastructure.SyncLedgerEntry;
import com.foundation.sync.infrastructure.SyncLedgerWriter;
import com.foundation.sync.infrastructure.SyncLogRepository;
import com.foundation.sync.infrastructure.SyncTaskRepository;
import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.IncrementalCursor;
import com.foundation.sync.spi.IncrementalMode;
import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncObjectType;
import com.foundation.sync.spi.SyncSourceAdapter;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 同步引擎（F-08 BAS-11 §4、§12）。
 *
 * <p>编排流水线：读能力矩阵与字段映射 → 拉取（全量分页 / 增量游标）→ 差异预演 → 删除保护阈值判定
 * → 幂等落地 → 写任务 / 日志 / 冲突 → 发事件 → 写台账。差异算法、阈值规则、幂等合并、字段映射
 * 全部复用 {@code domain} 层实现，预演与落地口径一致。
 *
 * <p>事务边界在本类（应用层）：落地、日志、冲突与 outbox 追加同一事务提交，保证事件与数据一致。
 *
 * <p>能力缺失（来源未声明某对象 / 未装载适配器）不静默跳过：记一条 {@code WARN} 日志并把任务标为
 * {@link SyncTaskStatus#PARTIAL}（{@code capability_missing = TRUE}）。
 */
public class SyncEngine {

    /** 分页保护上限：防止适配器实现缺陷导致死循环。 */
    private static final int MAX_PAGES = 10_000;

    private final SyncTaskRepository tasks;
    private final SyncConflictRepository conflicts;
    private final SyncFieldMappingRepository mappings;
    private final SyncLogRepository logs;
    private final MasterDataPort masterData;
    private final AdapterRegistry adapters;
    private final FieldMapper fieldMapper;
    private final DiffPreviewService diffPreview;
    private final DeleteProtectionPolicy deleteProtection;
    private final IdempotentUpserter upserter;
    private final SyncEventOutbox events;
    private final SyncLedgerWriter ledger;

    public SyncEngine(SyncTaskRepository tasks,
                      SyncConflictRepository conflicts,
                      SyncFieldMappingRepository mappings,
                      SyncLogRepository logs,
                      MasterDataPort masterData,
                      AdapterRegistry adapters,
                      FieldMapper fieldMapper,
                      DiffPreviewService diffPreview,
                      DeleteProtectionPolicy deleteProtection,
                      IdempotentUpserter upserter,
                      SyncEventOutbox events,
                      SyncLedgerWriter ledger) {
        this.tasks = tasks;
        this.conflicts = conflicts;
        this.mappings = mappings;
        this.logs = logs;
        this.masterData = masterData;
        this.adapters = adapters;
        this.fieldMapper = fieldMapper;
        this.diffPreview = diffPreview;
        this.deleteProtection = deleteProtection;
        this.upserter = upserter;
        this.events = events;
        this.ledger = ledger;
    }

    /**
     * 一次编排的结果。
     *
     * @param task  任务记录（已落库）
     * @param diff  差异结果（新增 / 修改 / 停用 / 移动）
     * @param pause 删除保护阈值是否触发
     */
    public record SyncOutcome(SyncTask task, DiffResult diff, boolean pause) {
    }

    /** 差异预演（dry-run）：写任务与日志，不落地、不写冲突。 */
    @Transactional
    public SyncOutcome preview(SyncSource source, SyncMode mode, SyncTriggerMode triggerMode, String operator) {
        return execute(source, mode, true, triggerMode, operator, false);
    }

    /** 手动 / 定时触发同步。 */
    @Transactional
    public SyncOutcome run(SyncSource source, SyncMode mode, SyncTriggerMode triggerMode, String operator) {
        return execute(source, mode, false, triggerMode, operator, false);
    }

    /**
     * 超阈值暂停后确认继续：重跑一次并跳过删除保护阈值。
     *
     * <p>因落地是幂等的，前次已落地的「新增 / 修改」重跑后不再产生差异，本次只补齐被暂停的停用。
     */
    @Transactional
    public SyncOutcome confirm(SyncSource source, SyncTask paused, String operator) {
        return execute(source, paused.mode(), false, paused.triggerMode(), operator, true);
    }

    // ------------------------------------------------------------------ 编排

    private SyncOutcome execute(SyncSource source, SyncMode mode, boolean dryRun, SyncTriggerMode triggerMode,
                                String operator, boolean thresholdConfirmed) {
        SyncMode effectiveMode = mode == null ? SyncMode.FULL : mode;
        SyncTriggerMode effectiveTrigger = triggerMode == null ? SyncTriggerMode.MANUAL : triggerMode;
        SyncTask task = new SyncTask(Ids.newId(SyncIdPrefix.TASK), source.id(), effectiveMode,
                effectiveTrigger, SyncTaskStatus.RUNNING, dryRun, null, 0, 0, 0, 0, false, null,
                Instant.now(), null, operator);
        tasks.insert(task);
        try {
            return land(source, task, thresholdConfirmed);
        } catch (BizException ex) {
            return new SyncOutcome(failed(source, task, ex.getErrorCode().name()), DiffResult.empty(), false);
        } catch (RuntimeException ex) {
            return new SyncOutcome(failed(source, task, ErrorCode.INTERNAL.name()), DiffResult.empty(), false);
        }
    }

    private SyncOutcome land(SyncSource source, SyncTask task, boolean thresholdConfirmed) {
        SyncSourceAdapter adapter = adapters.find(source.sourceType()).orElse(null);
        if (adapter != null) {
            adapter.authenticate(source.credentials());
        }
        List<FieldMapping> mappingList = mappings.listBySource(source.id());
        Set<SyncObjectType> declared = adapter == null
                ? Set.<SyncObjectType>of() : adapter.capabilities().objects();
        Set<SyncObjectType> wanted = new LinkedHashSet<>();
        mappingList.forEach(mapping -> wanted.add(mapping.objectType()));
        if (wanted.isEmpty()) {
            wanted.addAll(declared);
        }

        boolean capabilityMissing = false;
        List<SyncLog> collected = new ArrayList<>();
        DiffResult combined = DiffResult.empty();
        IncrementalCursor cursor = task.cursor();

        for (SyncObjectType objectType : wanted) {
            if (adapter == null || !declared.contains(objectType)) {
                capabilityMissing = true;
                collected.add(SyncLog.warn(task.id(), objectType, null, SyncAction.SKIP,
                        "来源未声明 " + objectType + " 能力，本次不生成差异（能力缺失）"));
                continue;
            }
            Pulled pulled = pull(adapter, objectType, task.mode(), cursor, source);
            cursor = pulled.cursor();
            List<MappedRecord> mapped = new ArrayList<>();
            for (ExternalRecord record : pulled.records()) {
                mapped.add(fieldMapper.map(record, mappingList));
            }
            combined = combined.merge(diffPreview.preview(objectType, mapped, source.sourceType(),
                    masterData, task.mode().isFullSnapshot()));
        }

        DeleteProtectionVerdict verdict = thresholdConfirmed
                ? DeleteProtectionVerdict.pass()
                : deleteProtection.evaluate(combined, existingPersons(source.sourceType()));

        if (!task.dryRun()) {
            List<DiffEntry> landable = new ArrayList<>();
            for (DiffEntry entry : combined.entries()) {
                if (verdict.pause() && entry.kind() == DiffKind.DISABLE) {
                    continue;
                }
                landable.add(entry);
            }
            UpsertOutcome outcome = upserter.land(landable, source, task.id(), masterData);
            collected.addAll(outcome.logs());
            if (outcome.hasConflicts()) {
                conflicts.insertAll(outcome.conflicts());
            }
            for (LandedChange change : outcome.changes()) {
                events.landed(change);
            }
            if (verdict.pause()) {
                collected.add(SyncLog.warn(task.id(), null, null, SyncAction.SKIP,
                        "停用差异超阈值，剩余停用暂停待确认：" + combined.countDisabled()));
            }
        }

        boolean paused = verdict.pause() && !task.dryRun();
        SyncTaskStatus status = paused ? SyncTaskStatus.PAUSED
                : (capabilityMissing ? SyncTaskStatus.PARTIAL : SyncTaskStatus.SUCCEEDED);
        SyncTask finished = task.withCounts(combined, capabilityMissing)
                .withStatus(status, paused ? SyncPauseReason.THRESHOLD : null, Instant.now());
        tasks.update(finished);
        if (!collected.isEmpty()) {
            logs.appendAll(collected);
        }
        if (paused) {
            events.syncPaused(task.id(), source.code(), SyncPauseReason.THRESHOLD, finished.counts());
        } else {
            events.syncCompleted(task.id(), source.code(), finished.counts(), capabilityMissing);
        }
        return new SyncOutcome(finished, combined, verdict.pause());
    }

    private SyncTask failed(SyncSource source, SyncTask task, String errorCode) {
        SyncTask failed = task.withStatus(SyncTaskStatus.FAILED, null, Instant.now());
        tasks.update(failed);
        events.syncFailed(task.id(), source.code(), errorCode);
        return failed;
    }

    // ------------------------------------------------------------------ 拉取

    /** 一次拉取结果。 */
    private record Pulled(List<ExternalRecord> records, IncrementalCursor cursor) {
    }

    private Pulled pull(SyncSourceAdapter adapter, SyncObjectType objectType, SyncMode mode,
                        IncrementalCursor startCursor, SyncSource source) {
        List<ExternalRecord> records = new ArrayList<>();
        if (mode != null && mode.isFullSnapshot()) {
            PageCursor cursor = PageCursor.first();
            for (int page = 0; page < MAX_PAGES; page++) {
                Page<ExternalRecord> result = adapter.fetchFull(objectType, cursor);
                records.addAll(result.items());
                if (!result.hasMore() || result.next() == null) {
                    break;
                }
                cursor = result.next();
            }
            ledger(source, objectType, "fetchFull");
            return new Pulled(records, startCursor);
        }
        IncrementalCursor cursor = startCursor == null || startCursor.mode() == null
                ? IncrementalCursor.initial(incrementalMode(adapter.capabilities())) : startCursor;
        for (int page = 0; page < MAX_PAGES; page++) {
            Page<ExternalRecord> result = adapter.fetchIncremental(objectType, cursor);
            records.addAll(result.items());
            cursor = adapter.advanceCursor(cursor, result);
            if (!result.hasMore() || result.next() == null) {
                break;
            }
        }
        ledger(source, objectType, "fetchIncremental");
        return new Pulled(records, cursor);
    }

    private static IncrementalMode incrementalMode(SourceCapabilities capabilities) {
        for (IncrementalMode mode : capabilities.incrementalModes()) {
            if (mode.supportsIncremental()) {
                return mode;
            }
        }
        return IncrementalMode.FULL_ONLY;
    }

    /** 出网台账（ADR-074 §9）：每次访问来源留一条 OUT 记录；默认实现为 {@code NoopSyncLedgerWriter}。 */
    private void ledger(SyncSource source, SyncObjectType objectType, String fetch) {
        String operation = "sync." + objectType.name().toLowerCase(Locale.ROOT) + "." + fetch;
        ledger.write(new SyncLedgerEntry(source.sourceType(), null, operation, null,
                source.credentialRef(), "OK", 0, Instant.now()));
    }

    private int existingPersons(String sourceType) {
        int total = 0;
        for (LandedRecord record : masterData.listBySource(SyncObjectType.PERSON, sourceType)) {
            if (!record.disabled()) {
                total++;
            }
        }
        return total;
    }
}
