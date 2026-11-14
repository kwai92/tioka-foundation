package com.foundation.audit.api;

/**
 * 审计保留类别（F-09 BAS-10 §数据模型；ADR-072 §7）：安全 / 配置 / 业务 / 运维。
 */
public enum RetentionClass {

    /** 安全类（登录、授权变更等，保留期最长）。 */
    SEC,
    /** 配置类（系统配置变更）。 */
    CFG,
    /** 业务类（业务数据操作）。 */
    BIZ,
    /** 运维类（归档、导出等运维动作）。 */
    OPS;

    /** 容错解析；空或未知一律按 {@link #SEC}（从严保留）。 */
    public static RetentionClass of(String value) {
        if (value == null || value.isBlank()) {
            return SEC;
        }
        for (RetentionClass retention : values()) {
            if (retention.name().equalsIgnoreCase(value.trim())) {
                return retention;
            }
        }
        return SEC;
    }
}
