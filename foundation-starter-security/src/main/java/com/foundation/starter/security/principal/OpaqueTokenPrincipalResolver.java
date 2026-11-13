package com.foundation.starter.security.principal;

import java.util.Optional;

/**
 * {@code opaque} 令牌来源（F-07 §2）：Tioka 延续 FND-05 的「OIDC 令牌换 {@code tka_} 不透明令牌」。
 *
 * <p>令牌内省由**应用提供**（BAS-20 落地）：应用注册自己的 {@link TokenIntrospector}
 * （或以自定义 {@link PrincipalResolver} Bean 直接覆盖）。默认内省器返回空，即「无有效令牌」。
 */
public class OpaqueTokenPrincipalResolver implements PrincipalResolver {

    private final TokenExtractor tokenExtractor;
    private final TokenIntrospector tokenIntrospector;

    public OpaqueTokenPrincipalResolver(TokenExtractor tokenExtractor, TokenIntrospector tokenIntrospector) {
        this.tokenExtractor = tokenExtractor;
        this.tokenIntrospector = tokenIntrospector;
    }

    @Override
    public Optional<Principal> resolve(Object request) {
        return tokenExtractor.extract(request).flatMap(tokenIntrospector::introspect);
    }

    /** 不透明令牌内省（应用实现；默认返回空）。 */
    @FunctionalInterface
    public interface TokenIntrospector {

        /** 内省令牌得到身份；无效返回空。 */
        Optional<Principal> introspect(String token);

        /** 默认：无内省实现，视为无有效令牌。 */
        static TokenIntrospector none() {
            return token -> Optional.empty();
        }
    }
}
