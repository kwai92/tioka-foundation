package com.foundation.sync.scim.domain;

import java.util.Map;

/** SCIM 载荷取值工具（F-08 BAS-13）。 */
public final class ScimValues {

    private ScimValues() {
    }

    /** 取字符串值并去除首尾空白；空白返回 {@code null}。 */
    public static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /** 非空才写入（避免用 {@code null} 覆盖已有主数据字段）。 */
    public static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
