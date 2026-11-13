package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.Instant;
import java.util.List;

/**
 * 服务账号（F-05 BAS-04 §领域模型）：账号主体是 BAS-03 的 {@code fdn_account}（{@code type=SERVICE}），
 * 本对象只表达「账号与应用的绑定 + 可用权限码白名单」。
 *
 * <p>只用 {@code client_credentials}，不能交互登录、无密码（S15 §8.2）。
 */
public record ServiceAccount(String id,
                             String accountId,
                             String appId,
                             String clientId,
                             String name,
                             List<String> permissionCodes,
                             String ownerUserId,
                             String status,
                             Instant expiresAt) {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static ServiceAccount of(String id, String accountId, String appId, String clientId, String name,
                                    List<String> permissionCodes, String ownerUserId, Instant expiresAt) {
        if (accountId == null || accountId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "服务账号必须绑定账号主体（fdn_account.id）");
        }
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "服务账号名称不能为空");
        }
        return new ServiceAccount(id, accountId, appId, clientId, name.trim(),
                permissionCodes == null ? List.of() : List.copyOf(permissionCodes),
                ownerUserId, STATUS_ACTIVE, expiresAt);
    }

    /** 过期判定：{@code expiresAt} 为空表示不过期。 */
    public boolean expiredAt(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public ServiceAccount disabled() {
        return new ServiceAccount(id, accountId, appId, clientId, name, permissionCodes, ownerUserId,
                STATUS_DISABLED, expiresAt);
    }
}
