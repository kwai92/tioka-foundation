package com.foundation.sync.domain;

import com.foundation.sync.api.dto.SyncSourceStatus;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncCredentialsRef;

import java.time.Instant;
import java.util.Map;

/**
 * 同步来源（F-08 BAS-11 §8 {@code fdn_sync_source}）。
 *
 * <p>一个部署只有一个权威来源（{@code authority=TRUE} 的部分唯一索引强制，ADR-052 第 4 条）。
 *
 * @param id            来源 ID（{@code src_…}）
 * @param code          来源编码（全局唯一）
 * @param name          名称
 * @param sourceType    来源类型
 * @param authority     是否权威来源
 * @param credentialRef 凭证引用（ADR-042，只存引用）
 * @param adapterImpl   适配器类名
 * @param pluginSha256  插件包哈希（FND-12 校验）
 * @param capabilities  能力声明（{@code capabilities_json} 缓存）
 * @param config        来源私有配置（不含密钥）
 * @param status        状态
 * @param scheduleCron  定时计划，可空
 * @param updatedAt     最近更新时间
 */
public record SyncSource(String id,
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

    public SyncSource {
        capabilities = capabilities == null ? SourceCapabilities.none() : capabilities;
        config = config == null ? Map.of() : Map.copyOf(config);
        status = status == null ? SyncSourceStatus.ENABLED : status;
    }

    /** 是否启用。 */
    public boolean enabled() {
        return status == SyncSourceStatus.ENABLED;
    }

    /** 组装认证用凭证引用。 */
    public SyncCredentialsRef credentials() {
        return new SyncCredentialsRef(sourceType, credentialRef);
    }

    /** 覆盖能力声明（探测后回写）。 */
    public SyncSource withCapabilities(SourceCapabilities newCapabilities) {
        return new SyncSource(id, code, name, sourceType, authority, credentialRef, adapterImpl,
                pluginSha256, newCapabilities, config, status, scheduleCron, updatedAt);
    }
}
