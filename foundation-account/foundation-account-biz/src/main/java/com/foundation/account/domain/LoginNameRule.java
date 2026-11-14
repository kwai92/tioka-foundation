package com.foundation.account.domain;

import java.util.function.Predicate;

/**
 * 登录名生成规则（F-04 §8 {@code foundation.account.provision.login-name-rule}；单测 {@code LoginNameRuleTest}）。
 *
 * <p>规则模板支持占位符 {@code {employeeNo}}，缺省为 {@code {employeeNo}}。冲突时追加 {@code -2}、{@code -3} 后缀。
 */
public final class LoginNameRule {

    /** 缺省规则模板。 */
    public static final String DEFAULT_RULE = "{employeeNo}";

    private static final String EMPLOYEE_NO = "{employeeNo}";

    /** 冲突后缀上限，防止异常数据下无限循环。 */
    private static final int MAX_SUFFIX = 1000;

    private LoginNameRule() {
    }

    /** 按模板渲染登录名（未含占位符时按字面量处理）。 */
    public static String render(String rule, String employeeNo) {
        if (employeeNo == null || employeeNo.isBlank()) {
            throw new IllegalArgumentException("工号不能为空");
        }
        String template = rule == null || rule.isBlank() ? DEFAULT_RULE : rule.trim();
        return template.replace(EMPLOYEE_NO, employeeNo.trim());
    }

    /**
     * 生成不与现有登录名冲突的登录名。
     *
     * @param rule        规则模板（空则用 {@link #DEFAULT_RULE}）
     * @param employeeNo  工号
     * @param taken       判断登录名是否已被占用
     */
    public static String generate(String rule, String employeeNo, Predicate<String> taken) {
        String base = render(rule, employeeNo);
        if (!taken.test(base)) {
            return base;
        }
        for (int suffix = 2; suffix <= MAX_SUFFIX; suffix++) {
            String candidate = base + "-" + suffix;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("登录名冲突过多，无法生成：" + base);
    }

    /**
     * 登录名合法性：ASCII 字母 / 数字 / {@code . _ - @}，长度 1~128。
     *
     * <p>登录名会推送到 Keycloak 作为 {@code username}，Keycloak 仅约束 ASCII 字符集，
     * 故此处按 ASCII 判定（不使用 {@link Character#isLetterOrDigit(char)}，其会放行中文等 CJK 字符）。
     */
    public static boolean isValid(String loginName) {
        if (loginName == null || loginName.isBlank() || loginName.length() > 128) {
            return false;
        }
        for (int i = 0; i < loginName.length(); i++) {
            char c = loginName.charAt(i);
            boolean asciiAlnum = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            if (!asciiAlnum && c != '.' && c != '_' && c != '-' && c != '@') {
                return false;
            }
        }
        return true;
    }
}
