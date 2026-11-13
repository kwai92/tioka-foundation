package com.foundation.authz.api;

import java.time.Instant;

/**
 * 权限码视图（F-05 BAS-05 §领域模型；编码格式 {@code 资源:动作}）。
 *
 * @param id          权限码 ID（{@code pms_{ulid}}）
 * @param appId       所属应用
 * @param code        权限码（{@code 资源:动作}）
 * @param name        说明，可空
 * @param riskLevel   {@code LOW} / {@code MEDIUM} / {@code HIGH}
 * @param status      {@code ACTIVE} / {@code OFFLINE}
 * @param firstSeenAt 首次出现时间
 * @param offlineAt   下线时间，{@code null} 表示在线
 */
public record PermissionCodeView(String id,
                                 String appId,
                                 String code,
                                 String name,
                                 String riskLevel,
                                 String status,
                                 Instant firstSeenAt,
                                 Instant offlineAt) {
}
