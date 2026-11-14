package com.foundation.sync.scim.domain;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * SCIM 令牌作用域（F-08 BAS-13 §2）：限制 Users / Groups 的读写子集。
 *
 * <p>取值形如 {@code Users:read}、{@code Groups:write}、{@code Users:*}、{@code *}，
 * 逗号 / 分号 / 空白分隔；大小写不敏感。为空表示不限制（整表读写）。
 */
public final class ScimScope {

    /** 用户资源。 */
    public static final String USERS = "Users";

    /** 组资源。 */
    public static final String GROUPS = "Groups";

    private static final String WILDCARD = "*";
    private static final String ALL = "all";

    private final Set<String> scopes;

    private ScimScope(Set<String> scopes) {
        this.scopes = scopes;
    }

    /** 解析作用域串（{@code null} / 空白返回空集 = 不限制）。 */
    public static ScimScope parse(String raw) {
        Set<String> parsed = new LinkedHashSet<>();
        if (raw != null) {
            for (String token : raw.split("[,;\\s]+")) {
                String normalized = token.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                parsed.add(normalized);
            }
        }
        return new ScimScope(parsed);
    }

    /** 是否不与任何作用域约束（空集）。 */
    public boolean unrestricted() {
        return scopes.isEmpty();
    }

    /** 是否允许对某资源的某类操作（{@code resource} 取 {@link #USERS} / {@link #GROUPS}）。 */
    public boolean allows(String resource, boolean write) {
        if (scopes.isEmpty()) {
            return true;
        }
        String prefix = resource == null ? "" : resource.toLowerCase(Locale.ROOT);
        if (scopes.contains(WILDCARD) || scopes.contains(ALL)) {
            return true;
        }
        if (scopes.contains(prefix + ":" + WILDCARD)) {
            return true;
        }
        // 写权限蕴含读权限
        if (scopes.contains(prefix + ":write")) {
            return true;
        }
        return !write && scopes.contains(prefix + ":read");
    }

    /** 归一化后的作用域串（用于持久化）。 */
    public String csv() {
        return String.join(",", scopes);
    }
}
