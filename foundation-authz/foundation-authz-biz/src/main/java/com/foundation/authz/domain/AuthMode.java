package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/**
 * 子系统授权模式（F-05 BAS-27 §四种授权模式）。
 *
 * <p>{@link #CONCENTRATED} 集中授权、{@link #GRADED} 分级授权、{@link #HYBRID} 混合、{@link #SELF} 自管。
 */
public enum AuthMode {

    /** 集中：全部由 Foundation 授权中心维护。 */
    CONCENTRATED,
    /** 分级：由组织分级管理员在范围内维护。 */
    GRADED,
    /** 混合：集中与自管并存。 */
    HYBRID,
    /** 自管：子系统自行维护授权。 */
    SELF;

    /** 解析入参；空值或非法值抛 {@code BAD_REQUEST}。 */
    public static AuthMode of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "授权模式不能为空（CONCENTRATED/GRADED/HYBRID/SELF）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法授权模式：" + value);
        }
    }
}
