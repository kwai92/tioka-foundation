package com.foundation.authz.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

/** 自管一次性导入批次状态（F-05 BAS-27 §自管导入）。 */
public enum SelfImportStatus {

    /** 已预览，尚未生效。 */
    PREVIEW,
    /** 已应用。 */
    APPLIED,
    /** 已丢弃。 */
    DISCARDED;

    /** 解析入参；空值或非法值抛 {@code BAD_REQUEST}。 */
    public static SelfImportStatus of(String value) {
        if (value == null || value.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "导入批次状态不能为空（PREVIEW/APPLIED/DISCARDED）");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "非法导入批次状态：" + value);
        }
    }
}
