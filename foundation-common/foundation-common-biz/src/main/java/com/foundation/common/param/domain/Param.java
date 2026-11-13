package com.foundation.common.param.domain;

/**
 * 参数。
 *
 * @param id          主键 {@code pm_{ulid}}
 * @param appCode     归属应用；{@code null} 表示全局
 * @param paramKey    参数键（作用域内唯一）
 * @param storedValue 落库值；{@code secret=true} 时为 AES-256-GCM 密文
 * @param secret      是否敏感（只写不读）
 * @param description 说明
 */
public record Param(String id, String appCode, String paramKey, String storedValue, boolean secret,
                    String description) {
}
