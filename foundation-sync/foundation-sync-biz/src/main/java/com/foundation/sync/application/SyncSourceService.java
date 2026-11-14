package com.foundation.sync.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.sync.api.SyncSourceApi;
import com.foundation.sync.api.dto.DiffItemView;
import com.foundation.sync.api.dto.DiffPreviewView;
import com.foundation.sync.api.dto.FieldMappingReplaceCmd;
import com.foundation.sync.api.dto.FieldMappingView;
import com.foundation.sync.api.dto.SyncMode;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncRunCmd;
import com.foundation.sync.api.dto.SyncSourceCreateCmd;
import com.foundation.sync.api.dto.SyncSourceStatus;
import com.foundation.sync.api.dto.SyncSourceUpdateCmd;
import com.foundation.sync.api.dto.SyncSourceView;
import com.foundation.sync.api.dto.SyncTaskView;
import com.foundation.sync.api.dto.SyncTriggerMode;
import com.foundation.sync.domain.DiffResult;
import com.foundation.sync.domain.FieldMapping;
import com.foundation.sync.domain.SyncIdPrefix;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.domain.SyncTask;
import com.foundation.sync.infrastructure.AdapterRegistry;
import com.foundation.sync.infrastructure.SyncFieldMappingRepository;
import com.foundation.sync.infrastructure.SyncSourceRepository;
import com.foundation.sync.spi.ProbeResult;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncSourceAdapter;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 同步来源应用服务（F-08 BAS-11 §10）：来源登记、能力探测、字段映射、差异预演与手动触发。
 *
 * <p>编排核心在 {@link SyncEngine}；本类只做来源侧校验、视图拼装与事务边界。
 */
public class SyncSourceService implements SyncSourceApi {

    private final SyncSourceRepository sources;
    private final SyncFieldMappingRepository mappings;
    private final AdapterRegistry adapters;
    private final SyncEngine engine;

    public SyncSourceService(SyncSourceRepository sources, SyncFieldMappingRepository mappings,
                             AdapterRegistry adapters, SyncEngine engine) {
        this.sources = sources;
        this.mappings = mappings;
        this.adapters = adapters;
        this.engine = engine;
    }

    @Override
    public List<SyncSourceView> listSources() {
        return sources.list().stream().map(SyncViews::source).toList();
    }

    @Override
    @Transactional
    public SyncSourceView createSource(SyncSourceCreateCmd cmd, String operator) {
        if (cmd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String code = requireText(cmd.code(), "code");
        String name = requireText(cmd.name(), "name");
        String sourceType = requireText(cmd.sourceType(), "sourceType");
        String adapterImpl = requireText(cmd.adapterImpl(), "adapterImpl");
        if (sources.findByCode(code).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "来源编码已存在: " + code);
        }
        boolean authority = Boolean.TRUE.equals(cmd.authority());
        requireAuthorityAvailable(authority, null);

        SourceCapabilities capabilities = adapters.find(sourceType)
                .map(SyncSourceAdapter::capabilities).orElseGet(SourceCapabilities::none);
        SyncSource created = new SyncSource(Ids.newId(SyncIdPrefix.SOURCE), code, name, sourceType,
                authority, cmd.credentialRef(), adapterImpl, null, capabilities,
                cmd.config() == null ? Map.of() : cmd.config(), SyncSourceStatus.ENABLED,
                cmd.scheduleCron(), null);
        sources.insert(created, operator);
        return SyncViews.source(sources.find(created.id()).orElse(created));
    }

    @Override
    @Transactional
    public SyncSourceView updateSource(String id, SyncSourceUpdateCmd cmd, String operator) {
        if (cmd == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        SyncSource current = requireSource(id);
        boolean authority = cmd.authority() == null ? current.authority() : cmd.authority();
        requireAuthorityAvailable(authority, id);
        SyncSource updated = new SyncSource(current.id(), current.code(),
                cmd.name() == null ? current.name() : cmd.name(), current.sourceType(), authority,
                cmd.credentialRef() == null ? current.credentialRef() : cmd.credentialRef(),
                cmd.adapterImpl() == null ? current.adapterImpl() : cmd.adapterImpl(),
                current.pluginSha256(), current.capabilities(),
                cmd.config() == null ? current.config() : cmd.config(),
                cmd.status() == null ? current.status() : cmd.status(),
                cmd.scheduleCron() == null ? current.scheduleCron() : cmd.scheduleCron(),
                current.updatedAt());
        sources.update(updated, operator);
        return SyncViews.source(sources.find(id).orElse(updated));
    }

    @Override
    @Transactional
    public ProbeResult probe(String id) {
        SyncSource source = requireSource(id);
        SyncSourceAdapter adapter = adapters.find(source.sourceType()).orElse(null);
        if (adapter == null) {
            return ProbeResult.failed(List.of(ProbeResult.ProbeItem.fail("adapter",
                    "未装载来源类型 " + source.sourceType() + " 的适配器（检查 foundation.sync.adapters）")));
        }
        adapter.authenticate(source.credentials());
        ProbeResult result = adapter.probe();
        if (result.reachable()) {
            sources.updateCapabilities(id, adapter.capabilities());
        }
        return result;
    }

    @Override
    public List<FieldMappingView> fieldMappings(String sourceId) {
        requireSource(sourceId);
        return mappings.listBySource(sourceId).stream().map(SyncViews::fieldMapping).toList();
    }

    @Override
    @Transactional
    public List<FieldMappingView> replaceFieldMappings(String sourceId, FieldMappingReplaceCmd cmd,
                                                       String operator) {
        requireSource(sourceId);
        List<FieldMappingReplaceCmd.Item> items = cmd == null ? List.of() : cmd.mappings();
        List<FieldMapping> targets = new ArrayList<>();
        for (FieldMappingReplaceCmd.Item item : items) {
            if (item.objectType() == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "字段映射缺少 objectType");
            }
            targets.add(new FieldMapping(Ids.newId(SyncIdPrefix.FIELD_MAPPING), sourceId, item.objectType(),
                    requireText(item.externalField(), "externalField"),
                    requireText(item.internalField(), "internalField"),
                    item.defaultValue(), item.transform(), item.required()));
        }
        mappings.replaceAll(sourceId, targets);
        return targets.stream().map(SyncViews::fieldMapping).toList();
    }

    @Override
    @Transactional
    public DiffPreviewView preview(String sourceId, SyncRunCmd cmd, String operator) {
        SyncSource source = requireSource(sourceId);
        SyncEngine.SyncOutcome outcome = engine.preview(source, modeOf(cmd), SyncTriggerMode.MANUAL,
                operator);
        return previewView(sourceId, outcome);
    }

    @Override
    @Transactional
    public SyncTaskView run(String sourceId, SyncRunCmd cmd, String operator) {
        SyncSource source = requireSource(sourceId);
        if (!source.enabled()) {
            throw new BizException(ErrorCode.CONFLICT, "来源已停用，无法触发同步: " + source.code());
        }
        boolean dryRun = cmd != null && Boolean.TRUE.equals(cmd.dryRun());
        SyncEngine.SyncOutcome outcome = dryRun
                ? engine.preview(source, modeOf(cmd), SyncTriggerMode.MANUAL, operator)
                : engine.run(source, modeOf(cmd), SyncTriggerMode.MANUAL, operator);
        return SyncViews.task(outcome.task(), source.code());
    }

    // ------------------------------------------------------------------ 内部

    private DiffPreviewView previewView(String sourceId, SyncEngine.SyncOutcome outcome) {
        SyncTask task = outcome.task();
        DiffResult diff = outcome.diff();
        List<DiffItemView> items = diff.entries().stream().map(SyncViews::diffItem).toList();
        return new DiffPreviewView(sourceId, task.id(), diff.countCreated(), diff.countUpdated(),
                diff.countDisabled(), diff.countMoved(), outcome.pause(),
                outcome.pause() ? SyncPauseReason.THRESHOLD : null, task.capabilityMissing(), items);
    }

    private static SyncMode modeOf(SyncRunCmd cmd) {
        SyncMode mode = cmd == null ? null : cmd.mode();
        return mode == null ? SyncMode.FULL : mode;
    }

    private SyncSource requireSource(String id) {
        return sources.find(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "同步来源不存在: " + id));
    }

    /** 一个部署只有一个权威来源（{@code uq_sync_source_authority}）；切换是显式管理动作。 */
    private void requireAuthorityAvailable(boolean authority, String selfId) {
        if (!authority) {
            return;
        }
        sources.findAuthority()
                .filter(existing -> selfId == null || !existing.id().equals(selfId))
                .ifPresent(existing -> {
                    throw new BizException(ErrorCode.CONFLICT, "已存在权威来源: " + existing.code());
                });
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少必填参数: " + field);
        }
        return value.trim();
    }
}
