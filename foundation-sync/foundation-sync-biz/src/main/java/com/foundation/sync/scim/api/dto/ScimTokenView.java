package com.foundation.sync.scim.api.dto;

import java.time.Instant;

/**
 * SCIM 令牌视图（F-08 BAS-13 §5 管理面）。
 *
 * <p>{@code token} 字段只在创建响应中出现一次（明文只显示一次）；列表响应恒为 {@code null}。
 */
public record ScimTokenView(String id,
                            String name,
                            String prefix,
                            String scopes,
                            String status,
                            Instant lastUsedAt,
                            Instant expiresAt,
                            Instant createAt,
                            String token) {
}
