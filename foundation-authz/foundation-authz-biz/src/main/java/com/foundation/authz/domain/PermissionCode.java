package com.foundation.authz.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 权限码（F-05 BAS-05 §领域模型）：编码为 {@code 资源:动作}，与 M1-01 权限点格式一致。
 *
 * @param id          权限码 ID（{@code pms_{ulid}}）
 * @param appId       所属应用
 * @param code        权限码
 * @param name        说明，可空
 * @param riskLevel   见 {@link RiskLevel}
 * @param status      见 {@link LifecycleStatus}
 * @param firstSeenAt 首次出现时间
 * @param offlineAt   下线时间，{@code null} 表示在线
 */
public record PermissionCode(String id,
                             String appId,
                             String code,
                             String name,
                             RiskLevel riskLevel,
                             String status,
                             Instant firstSeenAt,
                             Instant offlineAt) {

    public PermissionCode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(appId, "appId");
        Objects.requireNonNull(code, "code");
        riskLevel = riskLevel == null ? RiskLevel.LOW : riskLevel;
        status = status == null ? LifecycleStatus.ACTIVE : status;
    }

    /** 是否在线（在线项才参与授权）。 */
    public boolean active() {
        return LifecycleStatus.ACTIVE.equals(status);
    }
}
