package com.foundation.sync.scim.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.domain.DiffEntry;
import com.foundation.sync.domain.DiffPreviewService;
import com.foundation.sync.domain.DiffResult;
import com.foundation.sync.domain.LandedRecord;
import com.foundation.sync.domain.MappedRecord;
import com.foundation.sync.domain.MasterDataPort;
import com.foundation.sync.scim.domain.ScimConstants;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SCIM 资源落地（F-08 BAS-13 §3）：对单条推送复用 BAS-11 的差异算法与本地锁定口径，
 * 直接经 {@link MasterDataPort} 幂等落地（实时推送无同步任务，故不写 {@code fdn_sync_log}）。
 *
 * <p>差异（新增 / 修改 / 移动 / 停用）由 {@link DiffPreviewService} 依本地快照算出，
 * 被本地锁定字段的变更自动跳过（{@code local_locked_fields}）。
 */
public class ScimResourceWriter {

    private final MasterDataPort masterData;
    private final DiffPreviewService diffPreview;

    public ScimResourceWriter(MasterDataPort masterData, DiffPreviewService diffPreview) {
        this.masterData = masterData;
        this.diffPreview = diffPreview;
    }

    /** 计算并应用一条推送；返回落地后的记录；无差异时返回本地现状。 */
    public LandedRecord apply(MappedRecord mapped) {
        DiffResult diff = diffPreview.preview(mapped.objectType(), List.of(mapped),
                ScimConstants.SOURCE, masterData, false);
        LandedRecord landed = masterData.find(mapped.objectType(), mapped.key(ScimConstants.SOURCE)).orElse(null);
        for (DiffEntry entry : diff.entries()) {
            landed = apply(entry);
        }
        return landed;
    }

    /** 停用（差异保护通过后调用）。 */
    public LandedRecord disable(SyncObjectType objectType, String externalId) {
        SyncKey key = SyncKey.of(ScimConstants.SOURCE, externalId);
        LandedRecord existing = masterData.find(objectType, key)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "资源不存在：" + externalId));
        if (existing.disabled()) {
            return existing;
        }
        return masterData.disable(objectType, existing);
    }

    private LandedRecord apply(DiffEntry entry) {
        SyncKey key = SyncKey.of(ScimConstants.SOURCE, entry.externalId());
        return switch (entry.kind()) {
            case CREATE -> masterData.create(entry.objectType(), key, entry.fields(), parentKey(entry));
            case UPDATE -> {
                Map<String, Object> write = writable(entry);
                if (write.isEmpty()) {
                    yield require(entry);
                }
                yield masterData.update(entry.objectType(), require(entry), write);
            }
            case MOVE -> {
                LandedRecord moved = masterData.move(entry.objectType(), require(entry), parentKey(entry));
                Map<String, Object> write = writable(entry);
                yield write.isEmpty() ? moved : masterData.update(entry.objectType(), moved, write);
            }
            case DISABLE -> masterData.disable(entry.objectType(), require(entry));
        };
    }

    private LandedRecord require(DiffEntry entry) {
        return masterData.find(entry.objectType(), SyncKey.of(ScimConstants.SOURCE, entry.externalId()))
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "本地记录不存在，无法落地: " + entry.externalId()));
    }

    private static SyncKey parentKey(DiffEntry entry) {
        return entry.parentExternalId() == null || entry.parentExternalId().isBlank()
                ? null : SyncKey.of(ScimConstants.SOURCE, entry.parentExternalId());
    }

    private static Map<String, Object> writable(DiffEntry entry) {
        Map<String, Object> write = new LinkedHashMap<>();
        for (String field : entry.writableFields()) {
            write.put(field, entry.fields().get(field));
        }
        return write;
    }
}
