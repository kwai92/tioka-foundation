package com.foundation.sync.scim.api.dto;

import java.time.Instant;

/**
 * 创建 SCIM 令牌命令（F-08 BAS-13 §5 管理面 {@code POST /api/scim/tokens}）。
 *
 * @param name      名称（用途说明）
 * @param scopes    作用域串，如 {@code Users:read,Groups:write}；空表示不限制
 * @param expiresAt 过期时间，可空表示不过期
 */
public record ScimTokenCreateCmd(String name, String scopes, Instant expiresAt) {
}
