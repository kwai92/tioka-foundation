package com.foundation.auth.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 登录策略校验与规范化（F-04 BAS-15 §2）。
 *
 * <p>密码策略为 Keycloak {@code passwordPolicy} 语法：若干 {@code name(args)} 以 {@code and} 连接
 * （如 {@code length(8) and digits(1) and upperCase(1)}）。会话与并发取值做上界护栏，防止误配导致全站无法登录。
 */
public final class AuthPolicyRules {

    /** Keycloak passwordPolicy 语法：{@code name(args)} 以 {@code and} 连接。 */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("[A-Za-z]+\\s*\\([^()]+\\)(\\s+and\\s+[A-Za-z]+\\s*\\([^()]+\\))*");

    /** 会话空闲超时上界（分钟）。 */
    private static final int MAX_IDLE_MINUTES = 24 * 60;

    /** 会话最长有效上界（小时）。 */
    private static final int MAX_SESSION_HOURS = 24 * 30;

    private AuthPolicyRules() {
    }

    /** 校验并返回密码策略字符串。 */
    public static String requirePasswordPolicy(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "密码策略不能为空");
        }
        if (!PASSWORD_POLICY.matcher(trimmed).matches()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "密码策略格式非法（应为 name(args) 以 and 连接）：" + value);
        }
        return trimmed;
    }

    /** 校验会话空闲超时（分钟）：{@code 1..1440}。 */
    public static int requireSessionIdleMinutes(Integer value) {
        int minutes = value == null ? 0 : value;
        if (minutes < 1 || minutes > MAX_IDLE_MINUTES) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "会话空闲超时（分钟）须在 1.." + MAX_IDLE_MINUTES + "：" + value);
        }
        return minutes;
    }

    /** 校验会话最长有效（小时）：{@code 1..720}。 */
    public static int requireSessionMaxHours(Integer value) {
        int hours = value == null ? 0 : value;
        if (hours < 1 || hours > MAX_SESSION_HOURS) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "会话最长有效（小时）须在 1.." + MAX_SESSION_HOURS + "：" + value);
        }
        return hours;
    }

    /** 校验并发会话上限：{@code 0} 表示不限，不得为负。 */
    public static int requireConcurrentSessions(Integer value) {
        int sessions = value == null ? 0 : value;
        if (sessions < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "并发会话上限不能为负：" + value);
        }
        return sessions;
    }

    /** 校验排序号：不得为负。 */
    public static int requireSortOrder(Integer value) {
        int order = value == null ? 0 : value;
        if (order < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "排序号不能为负：" + value);
        }
        return order;
    }

    /** 去重、去空白后的 TOTP 强制角色列表。 */
    public static List<String> normalizeTotpRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                out.add(role.trim());
            }
        }
        return List.copyOf(out);
    }

    /** 角色列表 → 逗号分隔存储串。 */
    public static String joinTotpRoles(List<String> roles) {
        return String.join(",", normalizeTotpRoles(roles));
    }

    /** 逗号分隔存储串 → 角色列表。 */
    public static List<String> parseTotpRoles(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return List.copyOf(out);
    }
}
