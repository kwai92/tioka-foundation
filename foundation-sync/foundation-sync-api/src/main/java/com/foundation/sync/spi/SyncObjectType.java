package com.foundation.sync.spi;

/**
 * 同步对象类型（F-08 BAS-11 §2，ADR-078 第 2 条）。
 *
 * <p>能力矩阵以本枚举声明「来源支持哪些对象」；引擎据此决定生成哪些差异、走哪种增量。
 */
public enum SyncObjectType {

    /** 组织单元（层级与顺序）。 */
    ORG_UNIT,

    /** 人员。 */
    PERSON,

    /** 岗位。 */
    POSITION,

    /** 任职。 */
    EMPLOYMENT,

    /** 职级。 */
    JOB_GRADE,

    /** 外部身份关联（{@code fdn_external_identity}）。 */
    EXTERNAL_IDENTITY;

    /**
     * 是否为「主数据」对象（决定非权威来源能否落地，ADR-052 第 4 条）。
     *
     * <p>非权威来源的落地范围被限制为「只写外部身份关联」，其余对象越界记为
     * {@code AUTHORITY_CONFLICT} 冲突。
     */
    public boolean isMasterData() {
        return this != EXTERNAL_IDENTITY;
    }

    /** 宽松解析：未知取值返回 {@code null}（用于反序列化能力缓存，不抛异常）。 */
    public static SyncObjectType of(String value) {
        if (value == null) {
            return null;
        }
        for (SyncObjectType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        return null;
    }
}
