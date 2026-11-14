package com.foundation.sync.scim.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import com.foundation.sync.domain.SyncIdPrefix;
import com.foundation.sync.scim.api.dto.ScimTokenCreateCmd;
import com.foundation.sync.scim.api.dto.ScimTokenView;
import com.foundation.sync.scim.domain.ScimConstants;
import com.foundation.sync.scim.domain.ScimHash;
import com.foundation.sync.scim.domain.ScimScope;
import com.foundation.sync.scim.domain.ScimToken;
import com.foundation.sync.scim.domain.ScimTokenStatus;
import com.foundation.sync.scim.domain.ScimValues;
import com.foundation.sync.scim.infrastructure.ScimTokenRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

/**
 * SCIM 令牌服务（F-08 BAS-13 §2、§5）：签发（明文只返回一次）、列表、回收与 Bearer 鉴权。
 *
 * <p>库内只存 SHA-256 哈希与识别前缀；鉴权时先按前缀定位行，再常量时间比对哈希（防时序侧信道），
 * 最后校验启用状态与过期时间，并把 {@code last_used_at} 回写。
 */
public class ScimTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BEARER = "bearer ";

    private final ScimTokenRepository tokens;
    private final Supplier<Instant> clock;

    public ScimTokenService(ScimTokenRepository tokens) {
        this(tokens, Instant::now);
    }

    public ScimTokenService(ScimTokenRepository tokens, Supplier<Instant> clock) {
        this.tokens = tokens;
        this.clock = clock;
    }

    /** 签发令牌；返回视图携带明文（**只此一次**）。 */
    public ScimTokenView issue(ScimTokenCreateCmd cmd, String operator) {
        String name = ScimValues.text(cmd == null ? null : cmd.name());
        if (name == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "令牌名称不能为空");
        }
        String secret = newSecret();
        ScimToken token = new ScimToken(Ids.newId(SyncIdPrefix.SCIM_TOKEN), name,
                ScimHash.sha256Hex(secret), secret.substring(0, ScimConstants.TOKEN_PREFIX_LENGTH),
                ScimScope.parse(cmd.scopes()).csv(), ScimTokenStatus.ENABLED, null, cmd.expiresAt(), clock.get());
        tokens.insert(token);
        return view(token, secret);
    }

    /** 全部令牌（不含明文）。 */
    public List<ScimTokenView> list() {
        return tokens.list().stream().map(token -> view(token, null)).toList();
    }

    /** 回收令牌。 */
    public void delete(String id) {
        ScimToken token = tokens.find(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "令牌不存在：" + id));
        tokens.delete(token.id());
    }

    /** Bearer 鉴权；不合法一律 {@code 401}。 */
    public ScimToken authenticate(String authorizationHeader) {
        String secret = bearer(authorizationHeader);
        if (secret == null || secret.length() < ScimConstants.TOKEN_PREFIX_LENGTH) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少或非法的 Bearer 令牌");
        }
        ScimToken token = tokens.findByPrefix(secret.substring(0, ScimConstants.TOKEN_PREFIX_LENGTH))
                .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "令牌无效"));
        if (!ScimHash.constantTimeEquals(token.tokenHash(), ScimHash.sha256Hex(secret))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌无效");
        }
        Instant now = clock.get();
        if (!token.usable(now)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "令牌已停用或过期");
        }
        tokens.touchLastUsed(token.id(), now);
        return token;
    }

    /** 作用域校验；越界 {@code 403}（{@code resource} 取 {@link ScimScope#USERS} / {@link ScimScope#GROUPS}）。 */
    public void requireScope(ScimToken token, String resource, boolean write) {
        if (!token.scopeView().allows(resource, write)) {
            throw new BizException(ErrorCode.FORBIDDEN,
                    "令牌作用域不允许 " + resource + (write ? " 写" : " 读"));
        }
    }

    private static ScimTokenView view(ScimToken token, String plaintext) {
        return new ScimTokenView(token.id(), token.name(), token.prefix(), token.scopes(),
                token.status().name(), token.lastUsedAt(), token.expiresAt(), token.createAt(), plaintext);
    }

    private static String bearer(String header) {
        if (header == null) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.length() <= BEARER.length() || !trimmed.toLowerCase().startsWith(BEARER)) {
            return null;
        }
        String secret = trimmed.substring(BEARER.length()).trim();
        return secret.isEmpty() ? null : secret;
    }

    private static String newSecret() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
