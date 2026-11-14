package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SourceCapabilities;

import java.time.Instant;
import java.util.Map;

/**
 * 同步来源视图（F-08 BAS-11 §10 {@code GET /api/sync/sources}）：含能力矩阵与状态。
 *
 * @param id            来源 ID（{@code src_…}）
 * @param code          来源编码
 * @param name          名称
 * @param sourceType    来源类型（{@code fdn_sync_source.source_type}）
 * @param authority     是否权威来源（一个部署至多一个）
 * @param credentialRef 凭证引用（不含密钥值）
 * @param adapterImpl   适配器类名
 * @param pluginSha256  插件包哈希
 * @param capabilities  能力声明
 * @param config        来源私有配置（不含密钥）
 * @param status        状态
 * @param scheduleCron  定时计划，可空
 * @param updatedAt     最近更新时间
 */
public record SyncSourceView(String id,
                             String code,
                             String name,
                             String sourceType,
                             boolean authority,
                             String credentialRef,
                             String adapterImpl,
                             String pluginSha256,
                             SourceCapabilities capabilities,
                             Map<String, Object> config,
                             SyncSourceStatus status,
                             String scheduleCron,
                             Instant updatedAt) {
}
