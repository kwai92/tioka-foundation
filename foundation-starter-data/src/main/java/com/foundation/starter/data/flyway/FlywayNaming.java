package com.foundation.starter.data.flyway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flyway 迁移命名约定（F-02 BAS-24 §foundation-starter-data）：{@code {prefix}{n}__{desc}.sql}。
 *
 * <p>口径与 F-02 BAS-01 一致：迁移目录 {@code src/main/resources/db/migration}，
 * 文件名形如 {@code V1__foundation_schema.sql}、{@code V7__org_unit_position_grade.sql}：
 * <ul>
 *   <li>前缀默认 {@code V}（可经 {@code foundation.data.flyway-prefix} 改写）；</li>
 *   <li>版本号为自 {@code 1} 起的正整数；</li>
 *   <li>描述为小写下划线式标识（{@code [a-z][a-z0-9_]*}），不用空格与中文，避免跨平台大小写差异。</li>
 * </ul>
 *
 * <p>号段约定（F-02 §1「迁移编号段」）：Foundation 库自 {@code V1} 起、独立 Flyway 历史表；
 * F2 批次（如 F-12/F-13）用 {@code V200+}；Tioka 侧用 {@code V270+}。号段只约束取号，
 * 不影响本类的命名与解析。
 *
 * <p>本类只做「命名与解析」，不执行迁移；号码是否可用由合入时的人工评审与 CI 检查保证。
 */
public class FlywayNaming {

    /** 默认前缀（{@code foundation.data.flyway-prefix} 的默认值）。 */
    public static final String DEFAULT_PREFIX = "V";

    private static final String DESCRIPTION = "[a-z][a-z0-9_]*";
    private static final String SEPARATOR = "__";

    private final String prefix;
    private final Pattern pattern;

    public FlywayNaming() {
        this(DEFAULT_PREFIX);
    }

    public FlywayNaming(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Flyway 迁移前缀不能为空");
        }
        this.prefix = prefix;
        this.pattern = Pattern.compile(
                "^" + Pattern.quote(prefix) + "(\\d+)" + SEPARATOR + "(" + DESCRIPTION + ")\\.sql$");
    }

    public String getPrefix() {
        return prefix;
    }

    /** 拼出迁移文件名，例如 {@code fileName(7, "org_unit_position_grade")} → {@code V7__org_unit_position_grade.sql}。 */
    public String fileName(long version, String description) {
        if (version <= 0) {
            throw new IllegalArgumentException("迁移版本号必须为正整数：" + version);
        }
        if (description == null || !description.matches(DESCRIPTION)) {
            throw new IllegalArgumentException("迁移描述必须为小写下划线式：" + description);
        }
        return prefix + version + SEPARATOR + description + ".sql";
    }

    /** 文件名是否符合本前缀的命名约定，如 {@code V1__foundation_schema.sql} → {@code true}。 */
    public boolean matches(String fileName) {
        return fileName != null && pattern.matcher(fileName).matches();
    }

    /** 取版本号；不符合命名约定时抛 {@link IllegalArgumentException}（让 CI 直接失败，不做静默兜底）。 */
    public long versionOf(String fileName) {
        return Long.parseLong(group(fileName, 1));
    }

    /** 取描述；不符合命名约定时抛 {@link IllegalArgumentException}。 */
    public String descriptionOf(String fileName) {
        return group(fileName, 2);
    }

    private String group(String fileName, int index) {
        Matcher matcher = fileName == null ? null : pattern.matcher(fileName);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalArgumentException(
                    "迁移文件名不符合约定（前缀 " + prefix + "，形如 " + prefix + "1__foundation_schema.sql）：" + fileName);
        }
        return matcher.group(index);
    }
}
