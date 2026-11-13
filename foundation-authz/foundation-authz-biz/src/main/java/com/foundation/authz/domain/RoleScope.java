package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 角色范围（F-05 BAS-05 §领域模型）：全局角色只含 Foundation 自身权限码（{@code fdn:*}）。 */
public enum RoleScope {

    /** 全局角色，跨应用生效，只允许含 {@code fdn:*} 权限码。 */
    SYSTEM,
    /** 应用内角色，绑定 {@code appId}。 */
    APP;

    /** 解析入参，非法值抛 {@code BAD_REQUEST}。 */
    public static RoleScope of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "角色范围不能为空（SYSTEM/APP）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法角色范围：" + value);
        }
    }
}
