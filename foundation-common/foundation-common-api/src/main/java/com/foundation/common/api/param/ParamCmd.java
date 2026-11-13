package com.foundation.common.api.param;

/**
 * 参数写命令。
 *
 * @param appCode     归属应用；{@code null} 表示全局
 * @param key         参数键（作用域内唯一）
 * @param value       明文值；{@code secret=true} 时由实现加密后落库
 * @param secret      是否敏感；{@code null} 视为 {@code false}
 * @param description 说明
 * @param operator    操作人
 */
public record ParamCmd(String appCode, String key, String value, Boolean secret, String description,
                       String operator) {
}
