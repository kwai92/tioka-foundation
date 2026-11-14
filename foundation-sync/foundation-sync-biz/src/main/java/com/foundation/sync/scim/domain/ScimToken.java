package com.foundation.sync.scim.domain;

import java.time.Instant;

/**
 * SCIM 服务端令牌（F-08 BAS-13 §5 {@code fdn_scim_token}）。
 *
 * <p>令牌明文只在创建时返回一次；库内只存 SHA-256 哈希与识别用前缀。{@code scopes} 限制
 * Users / Groups 的读写子集，{@code expiresAt} 为空表示不过期。
 *
 * @param id         令牌 ID（{@code sct_{ulid}}）
 * @param name       名称（用途说明）
 * @param tokenHash  SHA-256 十六进制小写
 * @param prefix     识别用前缀（明文前若干位）
 * @param scopes     作用域串（{@code Users:read,Groups:write}）
 * @param status     状态
 * @param lastUsedAt 最近使用时间，可空
 * @param expiresAt  过期时间，可空
 * @param createAt   创建时间
 */
public record ScimToken(String id,
                        String name,
                        String tokenHash,
                        String prefix,
                        String scopes,
                        ScimTokenStatus status,
                        Instant lastUsedAt,
                        Instant expiresAt,
                        Instant createAt) {

    public ScimToken {
        status = status == null ? ScimTokenStatus.ENABLED : status;
        scopes = scopes == null ? "" : scopes;
    }

    /** 是否启用且未过期。 */
    public boolean usable(Instant now) {
        if (status != ScimTokenStatus.ENABLED) {
            return false;
        }
        return expiresAt == null || now.isBefore(expiresAt);
    }

    /** 令牌作用域。 */
    public ScimScope scopeView() {
        return ScimScope.parse(scopes);
    }
}
