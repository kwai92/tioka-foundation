package com.foundation.sync.scim.domain;

import java.util.Locale;

/** SCIM 令牌状态（F-08 BAS-13 §5 {@code fdn_scim_token.status}）。 */
public enum ScimTokenStatus {

    /** 启用。 */
    ENABLED,

    /** 停用。 */
    DISABLED;

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static ScimTokenStatus of(String value) {
        if (value == null) {
            return null;
        }
        for (ScimTokenStatus status : values()) {
            if (status.name().equalsIgnoreCase(value.trim())) {
                return status;
            }
        }
        return null;
    }

    /** 大写规范化（持久化用）；空白返回 {@code null}。 */
    public static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
