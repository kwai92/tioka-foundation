package com.foundation.sync.scim.domain;

/**
 * SCIM 过滤运算符（F-08 BAS-13 §3，RFC 7644 子集）。
 *
 * <p>只支持 {@code eq} / {@code co} / {@code sw}；其余取值（{@code ne} / {@code gt} /
 * {@code and} …）一律视为不支持，由解析方返回 {@code 400}（不静默忽略）。
 */
public enum ScimFilterOperator {

    /** 相等（字符串比较大小写不敏感）。 */
    EQ("eq"),

    /** 包含。 */
    CO("co"),

    /** 前缀。 */
    SW("sw");

    private final String token;

    ScimFilterOperator(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static ScimFilterOperator of(String value) {
        if (value == null) {
            return null;
        }
        for (ScimFilterOperator operator : values()) {
            if (operator.token.equalsIgnoreCase(value.trim())) {
                return operator;
            }
        }
        return null;
    }
}
