package com.foundation.account.domain;

import com.foundation.account.api.dto.AccountStatus;

import java.util.List;

/**
 * Foundation 侧账号对账快照（F-04 §6）。
 *
 * @param accountId       账号 ID
 * @param loginName       期望登录名
 * @param keycloakUserId  Keycloak 用户 ID；为空表示尚未推送
 * @param expectedEnabled 期望 {@code enabled}（{@code status=NORMAL} 为 {@code true}）
 * @param requiredActions 期望存在的必需动作（子集语义：Keycloak 侧缺任一即为差异）
 */
public record FoundationAccountSnapshot(String accountId,
                                        String loginName,
                                        String keycloakUserId,
                                        boolean expectedEnabled,
                                        List<String> requiredActions) {

    /** 由账号构造快照：{@code status=NORMAL} 期望可登录，锁定 / 停用期望不可登录（F-04 §5）。 */
    public static FoundationAccountSnapshot of(Account account, List<String> requiredActions) {
        return new FoundationAccountSnapshot(account.id(), account.loginName(), account.keycloakUserId(),
                account.status() == AccountStatus.NORMAL,
                requiredActions == null ? List.of() : List.copyOf(requiredActions));
    }
}
