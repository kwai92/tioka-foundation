package com.foundation.account.domain;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;

import java.time.Instant;

/**
 * 账号（F-04 §1）。
 *
 * <p>账号是「人用来登录的凭证载体」，由 Foundation 推送到 Keycloak；Foundation 不保存口令。
 *
 * @param id             主键（{@code acc_…}）
 * @param loginName      登录名（全局唯一）
 * @param personId       关联人员 ID；应急管理员与服务账号为空
 * @param keycloakUserId Keycloak 用户 ID；待发时为空
 * @param status         状态
 * @param type           类型
 * @param lastLoginAt    最近登录时间，可空
 * @param version        行版本（事件版本）
 */
public record Account(String id,
                      String loginName,
                      String personId,
                      String keycloakUserId,
                      AccountStatus status,
                      AccountType type,
                      Instant lastLoginAt,
                      long version) {

    /** 转对外视图。 */
    public AccountView toView() {
        return new AccountView(id, loginName, personId, keycloakUserId, status, type, lastLoginAt, version);
    }

    /** 是否为个人账号。 */
    public boolean personal() {
        return type == AccountType.PERSONAL;
    }

    /** 是否可登录（已推送 Keycloak 且状态为 {@code NORMAL}）。 */
    public boolean loginEnabled() {
        return status == AccountStatus.NORMAL && keycloakUserId != null;
    }

    /** 变更状态并递增版本。 */
    public Account withStatus(AccountStatus newStatus) {
        return new Account(id, loginName, personId, keycloakUserId, newStatus, type, lastLoginAt, version + 1);
    }

    /** 回写 Keycloak 用户 ID 并递增版本。 */
    public Account withKeycloakUserId(String newKeycloakUserId) {
        return new Account(id, loginName, personId, newKeycloakUserId, status, type, lastLoginAt, version + 1);
    }
}
