package com.foundation.sync.spi;

import java.util.Objects;

/**
 * 幂等键 = 来源 + 外部 ID（F-08 BAS-11 §7，ADR-078 第 1、6 条）。
 *
 * <p>落地按该键在 {@code (source, external_id)} 上 upsert，同一批 / 重复批次零重复新增。
 *
 * @param sourceType 来源标识（{@code fdn_sync_source.source_type}，如 {@code FEISHU}）
 * @param externalId 来源侧对象标识
 */
public record SyncKey(String sourceType, String externalId) {

    public SyncKey {
        Objects.requireNonNull(sourceType, "sourceType");
        Objects.requireNonNull(externalId, "externalId");
    }

    /** 构造并校验非空白。 */
    public static SyncKey of(String sourceType, String externalId) {
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("sourceType required");
        }
        if (externalId == null || externalId.isBlank()) {
            throw new IllegalArgumentException("externalId required");
        }
        return new SyncKey(sourceType.trim(), externalId.trim());
    }

    /** 是否属于给定来源。 */
    public boolean belongsTo(String source) {
        return sourceType.equalsIgnoreCase(source == null ? null : source.trim());
    }
}
