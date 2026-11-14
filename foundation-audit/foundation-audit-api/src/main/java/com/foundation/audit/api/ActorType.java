package com.foundation.audit.api;

/**
 * 审计主体类型（F-09 BAS-10 §数据模型；ADR-072 §1）：人 / 服务 / 智能体 / 系统。
 */
public enum ActorType {

    /** 人（登录用户）。 */
    USER,
    /** 服务（服务账号 / 应用）。 */
    SERVICE,
    /** 智能体（AI Agent）。 */
    AGENT,
    /** 系统（无主体的系统动作）。 */
    SYSTEM;

    /** 容错解析；空或未知一律按 {@link #SYSTEM}。 */
    public static ActorType of(String value) {
        if (value == null || value.isBlank()) {
            return SYSTEM;
        }
        for (ActorType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return SYSTEM;
    }
}
