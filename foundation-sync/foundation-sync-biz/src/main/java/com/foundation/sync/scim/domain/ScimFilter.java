package com.foundation.sync.scim.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SCIM 过滤表达式（F-08 BAS-13 §3，RFC 7644 {@code filter} 子集）。
 *
 * <p>只支持单条件 {@code attribute eq|co|sw "value"}；不支持、语法错误或复合条件一律抛
 * {@link ErrorCode#BAD_REQUEST}（HTTP 400），**不静默忽略**。
 *
 * @param attribute 过滤属性（如 {@code userName}）
 * @param operator  运算符
 * @param value     过滤字面量（去引号）
 */
public record ScimFilter(String attribute, ScimFilterOperator operator, String value) {

    private static final Pattern EXPRESSION =
            Pattern.compile("^([A-Za-z][A-Za-z0-9._]*)\\s+(\\S+)\\s+\"(.*)\"$");

    /** 空过滤（不过滤任何记录）。 */
    public static ScimFilter none() {
        return new ScimFilter(null, null, null);
    }

    /** 是否携带有效过滤条件。 */
    public boolean present() {
        return attribute != null && operator != null;
    }

    /** 解析过滤表达式；{@code raw} 为空白时返回 {@link #none()}。 */
    public static ScimFilter parse(String raw) {
        return parse(raw, null);
    }

    /**
     * 解析过滤表达式并校验属性白名单。
     *
     * @param raw               过滤表达式原文（{@code filter} 查询参数）
     * @param allowedAttributes 允许的过滤属性；{@code null} 表示不校验
     */
    public static ScimFilter parse(String raw, Set<String> allowedAttributes) {
        if (raw == null || raw.isBlank()) {
            return none();
        }
        String trimmed = raw.trim();
        Matcher matcher = EXPRESSION.matcher(trimmed);
        if (!matcher.matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "不支持的单条件过滤表达式（应为 attribute eq|co|sw \"value\"）：" + trimmed);
        }
        String attribute = matcher.group(1);
        ScimFilterOperator operator = ScimFilterOperator.of(matcher.group(2));
        if (operator == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的过滤运算符：" + matcher.group(2));
        }
        String value = matcher.group(3);
        if (value.contains("\"")) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持复合过滤条件：" + trimmed);
        }
        if (allowedAttributes != null && !allowedAttributes.contains(attribute)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不支持的过滤属性：" + attribute);
        }
        return new ScimFilter(attribute, operator, value);
    }

    /** 用给定属性值判定是否命中（{@code eq} / {@code co} / {@code sw}，大小写不敏感）。 */
    public boolean matches(String attributeValue) {
        if (!present()) {
            return true;
        }
        if (attributeValue == null) {
            return false;
        }
        String actual = attributeValue.toLowerCase(Locale.ROOT);
        String expected = value.toLowerCase(Locale.ROOT);
        return switch (operator) {
            case EQ -> actual.equals(expected);
            case CO -> actual.contains(expected);
            case SW -> actual.startsWith(expected);
        };
    }
}
