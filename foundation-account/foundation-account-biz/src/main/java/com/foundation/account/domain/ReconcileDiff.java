package com.foundation.account.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对账差异（F-04 §6；单测 {@code ReconcileDiffTest}）。
 *
 * <p>以 Foundation 为准比对：存在性、{@code enabled}、{@code username}、必需动作；Keycloak 中多出的、
 * 非 Foundation 管理的账号记为孤立账号。Keycloak 客户端的服务账号用户（{@code service-account-…}）不属于
 * Foundation 管理的账号，予以排除。
 */
public record ReconcileDiff(List<DriftItem> drifts) {

    /** Keycloak 服务账号用户名前缀（客户端服务账号，非人员账号）。 */
    public static final String SERVICE_ACCOUNT_USERNAME_PREFIX = "service-account-";

    public ReconcileDiff {
        drifts = drifts == null ? List.of() : List.copyOf(drifts);
    }

    /** 差异条数。 */
    public int drifted() {
        return drifts.size();
    }

    /** 可由对账在本轮回正的差异（缺失用户交由推送重试）。 */
    public List<DriftItem> fixable() {
        return drifts.stream().filter(DriftItem::fixable).toList();
    }

    /**
     * 是否超过阈值保护（F-04 §6）：差异数 > {@code thresholdCount} 或占比 > {@code thresholdPercent}%。
     *
     * @param total             参与对账的账号总数
     * @param thresholdPercent  占比阈值（%，0 或负表示不启用）
     * @param thresholdCount    条数阈值（0 或负表示不启用）
     */
    public boolean exceedsThreshold(int total, int thresholdPercent, int thresholdCount) {
        if (drifted() == 0) {
            return false;
        }
        if (thresholdCount > 0 && drifted() > thresholdCount) {
            return true;
        }
        return thresholdPercent > 0 && total > 0 && (long) drifted() * 100 > (long) total * thresholdPercent;
    }

    /**
     * 计算差异。
     *
     * @param foundation Foundation 侧快照
     * @param keycloak   Keycloak 侧快照
     */
    public static ReconcileDiff compute(List<FoundationAccountSnapshot> foundation,
                                        List<KeycloakAccountSnapshot> keycloak) {
        List<DriftItem> items = new ArrayList<>();
        Map<String, KeycloakAccountSnapshot> byId = new HashMap<>();
        if (keycloak != null) {
            for (KeycloakAccountSnapshot user : keycloak) {
                if (user.keycloakUserId() != null) {
                    byId.put(user.keycloakUserId(), user);
                }
            }
        }
        Set<String> managedIds = new HashSet<>();
        if (foundation != null) {
            for (FoundationAccountSnapshot account : foundation) {
                String kcId = account.keycloakUserId();
                if (kcId == null || kcId.isBlank()) {
                    items.add(new DriftItem(account.accountId(), account.loginName(), DriftKind.MISSING_IN_KEYCLOAK,
                            "Foundation 有账号但尚未推送 Keycloak"));
                    continue;
                }
                managedIds.add(kcId);
                KeycloakAccountSnapshot user = byId.get(kcId);
                if (user == null) {
                    items.add(new DriftItem(account.accountId(), account.loginName(), DriftKind.MISSING_IN_KEYCLOAK,
                            "Keycloak 中不存在该用户：" + kcId));
                    continue;
                }
                if (user.enabled() != account.expectedEnabled()) {
                    items.add(new DriftItem(account.accountId(), account.loginName(), DriftKind.ENABLED_MISMATCH,
                            "期望 enabled=" + account.expectedEnabled() + "，实际 " + user.enabled()));
                }
                if (account.loginName() != null && !account.loginName().equals(user.username())) {
                    items.add(new DriftItem(account.accountId(), account.loginName(), DriftKind.USERNAME_MISMATCH,
                            "期望登录名 " + account.loginName() + "，实际 " + user.username()));
                }
                List<String> missing = account.requiredActions().stream()
                        .filter(action -> !user.requiredActions().contains(action))
                        .toList();
                if (!missing.isEmpty()) {
                    items.add(new DriftItem(account.accountId(), account.loginName(),
                            DriftKind.REQUIRED_ACTIONS_MISMATCH, "Keycloak 缺少必需动作：" + missing));
                }
            }
        }
        if (keycloak != null) {
            for (KeycloakAccountSnapshot user : keycloak) {
                if (user.keycloakUserId() == null || managedIds.contains(user.keycloakUserId())) {
                    continue;
                }
                String username = user.username() == null ? "" : user.username();
                if (username.startsWith(SERVICE_ACCOUNT_USERNAME_PREFIX)) {
                    continue;
                }
                items.add(new DriftItem(null, username, DriftKind.ORPHAN_IN_KEYCLOAK,
                        "Keycloak 中存在非 Foundation 管理的账号：" + username));
            }
        }
        return new ReconcileDiff(items);
    }
}
