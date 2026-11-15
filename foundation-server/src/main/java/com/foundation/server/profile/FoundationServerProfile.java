package com.foundation.server.profile;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Foundation 服务端装配 profile（36 号规划 ADP-05 / §6「装配 profile」）。
 *
 * <p>profile <b>只影响 foundation-server 自身</b>：部署时按需要装配的业务域裁剪服务端的
 * Bean、控制器与定时任务；接入方（业务项目）永远通过「按需引 starter + 能力开关」裁剪，
 * 不受本枚举约束。
 *
 * <p>每个域对应各 {@code -biz} 模块 {@code AutoConfiguration} 上的
 * {@code foundation.server.domains.<domain>} 条件；缺省均为 true，因此未设置 profile 时
 * 与改造前的「全量装配」行为完全一致。
 *
 * <p><b>依赖约束</b>：{@code common} 域提供 {@code EventProperties} 等公共 Bean，是
 * {@code org / account / authz / notify / sync} 的前置；因此四种 profile 都包含它。
 * {@code app} 域提供 {@code AppApi}，是 {@code authz} 的前置。
 */
public enum FoundationServerProfile {

    /** 全量：A 类核心平台与中台自身。 */
    FULL("full"),

    /** 身份与权限：组织、账号、权限、授权快照；不含文件、日历、消息、同步。 */
    IAM("iam"),

    /** 公共服务：字典、参数、编号、审计。 */
    COMMON("common"),

    /** 最小形态：字典、参数、编号，供 D 类轻量服务使用。 */
    MINIMAL("minimal");

    /** 全部可裁剪域（顺序稳定，供测试与文档生成使用）。 */
    public static final Set<String> ALL_DOMAINS = domains(
            "common", "app", "org", "account", "authz", "auth", "notify", "audit",
            "file", "calendar", "sequence", "sync", "scim", "feishu", "ldap");

    private static final Set<String> MINIMAL_DOMAINS = domains("common", "sequence");
    private static final Set<String> COMMON_DOMAINS = domains("common", "sequence", "audit");
    private static final Set<String> IAM_DOMAINS = domains(
            "common", "app", "org", "account", "authz", "auth");

    private final String key;

    FoundationServerProfile(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 该 profile 是否装配指定域。 */
    public boolean enables(String domain) {
        if (!ALL_DOMAINS.contains(domain)) {
            throw new IllegalArgumentException("未知的 Foundation 业务域：" + domain
                    + "，可选：" + ALL_DOMAINS);
        }
        return switch (this) {
            case FULL -> true;
            case IAM -> IAM_DOMAINS.contains(domain);
            case COMMON -> COMMON_DOMAINS.contains(domain);
            case MINIMAL -> MINIMAL_DOMAINS.contains(domain);
        };
    }

    /** 该 profile 装配的域集合（按 {@link #ALL_DOMAINS} 顺序）。 */
    public Set<String> enabledDomains() {
        Set<String> result = new LinkedHashSet<>();
        for (String domain : ALL_DOMAINS) {
            if (enables(domain)) {
                result.add(domain);
            }
        }
        return result;
    }

    /** 解析配置值；空值按 full，未知值快速失败而不是悄悄降级。 */
    public static FoundationServerProfile from(String raw) {
        if (raw == null || raw.isBlank()) {
            return FULL;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (FoundationServerProfile profile : values()) {
            if (profile.key.equals(normalized)) {
                return profile;
            }
        }
        throw new IllegalArgumentException("非法的 foundation.server.profile=" + raw
                + "，可选 full / iam / common / minimal");
    }

    private static Set<String> domains(String... values) {
        return java.util.Collections.unmodifiableSet(
                new LinkedHashSet<>(java.util.Arrays.asList(values)));
    }
}
