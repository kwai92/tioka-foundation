package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 菜单类型（F-05 BAS-05 §领域模型）。 */
public enum MenuType {

    /** 目录。 */
    DIR,
    /** 菜单（对应一个可授权功能）。 */
    MENU,
    /** 按钮（操作级入口）。 */
    BUTTON;

    /** 解析清单声明值，非法值抛 {@code BAD_REQUEST}。 */
    public static MenuType of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "菜单类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法菜单类型：" + value);
        }
    }
}
