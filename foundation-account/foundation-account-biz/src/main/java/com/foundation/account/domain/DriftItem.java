package com.foundation.account.domain;

/**
 * 对账差异项（F-04 §6）。
 *
 * @param accountId 账号 ID（{@code ORPHAN_IN_KEYCLOAK} 时为空）
 * @param loginName 登录名（登录名漂移时为 Keycloak 侧值）
 * @param kind      差异类别
 * @param detail    差异说明（写入 {@code detail_json}）
 */
public record DriftItem(String accountId, String loginName, DriftKind kind, String detail) {

    /** 是否可由对账修复：缺失用户交由推送重试，其余在本轮回正。 */
    public boolean fixable() {
        return kind != DriftKind.MISSING_IN_KEYCLOAK;
    }
}
