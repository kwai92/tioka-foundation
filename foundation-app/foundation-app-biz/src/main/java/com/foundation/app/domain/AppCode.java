package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * 应用编码值对象（F-05 BAS-04 §领域模型）：正则 {@code ^[a-z][a-z0-9-]{1,31}$}，登记后不可改。
 */
public record AppCode(String value) {

    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    public static AppCode of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "应用编码不能为空");
        }
        String normalized = raw.trim();
        if (!PATTERN.matcher(normalized).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "应用编码需匹配 ^[a-z][a-z0-9-]{1,31}$（小写字母开头，2〜32 位）：" + normalized);
        }
        return new AppCode(normalized);
    }
}
