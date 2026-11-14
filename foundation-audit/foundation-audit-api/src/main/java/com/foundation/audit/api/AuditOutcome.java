package com.foundation.audit.api;

/**
 * 审计结果（F-09 BAS-10 §数据模型；ADR-072 §1）：成功 / 失败 / 拒绝 / 异常。
 */
public enum AuditOutcome {

    /** 成功。 */
    SUCCESS,
    /** 失败（业务失败）。 */
    FAILURE,
    /** 拒绝（权限或策略拒绝）。 */
    DENIED,
    /** 异常（未预期错误）。 */
    ERROR;

    /** 容错解析；空或未知一律按 {@link #ERROR}。 */
    public static AuditOutcome of(String value) {
        if (value == null || value.isBlank()) {
            return ERROR;
        }
        for (AuditOutcome outcome : values()) {
            if (outcome.name().equalsIgnoreCase(value.trim())) {
                return outcome;
            }
        }
        return ERROR;
    }
}
