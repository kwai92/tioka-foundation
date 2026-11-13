package com.foundation.common.dict.domain;

/**
 * 字典类型。
 *
 * @param id      主键 {@code dt_{ulid}}
 * @param appCode 归属应用；{@code null} 表示全局
 * @param code    类型编码（作用域内唯一）
 * @param name    类型名称
 * @param status  {@code ENABLED} / {@code DISABLED}
 */
public record DictType(String id, String appCode, String code, String name, String status) {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";
}
