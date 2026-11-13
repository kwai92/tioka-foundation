package com.foundation.common.api.dict;

/**
 * 字典类型写命令。
 *
 * @param appCode  归属应用；{@code null} 表示全局
 * @param code     类型编码（作用域内唯一）
 * @param name     类型名称
 * @param status   {@code ENABLED} / {@code DISABLED}；{@code null} 视为 {@code ENABLED}
 * @param operator 操作人（写审计字段与事件）
 */
public record DictTypeCmd(String appCode, String code, String name, String status, String operator) {
}
