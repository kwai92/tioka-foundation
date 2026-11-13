package com.foundation.starter.security.principal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.foundation.starter.security.config.FoundationSecurityProperties;

import java.text.ParseException;
import java.util.Optional;

/**
 * {@code jwt} 令牌来源（F-07 §2、ADR-029 第 32 条）：把 IdP 令牌当资源服务器校验。
 *
 * <p>starter 只验签、验 {@code aud} / {@code iss}、提取身份声明；**不解析权限**（权限来自快照）。
 * 任何失败一律返回 {@link Optional#empty()}，由上层按未登录处理。
 */
public class JwtPrincipalResolver implements PrincipalResolver {

    private final ConfigurableJWTProcessor<SecurityContext> processor;
    private final FoundationSecurityProperties properties;
    private final TokenExtractor tokenExtractor;

    public JwtPrincipalResolver(FoundationSecurityProperties properties,
                                TokenExtractor tokenExtractor,
                                JWSKeySelector<SecurityContext> keySelector) {
        this.properties = properties;
        this.tokenExtractor = tokenExtractor;
        this.processor = new DefaultJWTProcessor<>();
        this.processor.setJWSKeySelector(keySelector);
    }

    /** 便于自定义 JWK 源（如本地缓存或静态公钥集）。 */
    public JwtPrincipalResolver(FoundationSecurityProperties properties,
                                TokenExtractor tokenExtractor,
                                JWKSource<SecurityContext> jwkSource,
                                java.util.Set<com.nimbusds.jose.JWSAlgorithm> algorithms) {
        this(properties, tokenExtractor,
                new com.nimbusds.jose.proc.JWSVerificationKeySelector<>(algorithms, jwkSource));
    }

    @Override
    public Optional<Principal> resolve(Object request) {
        return tokenExtractor.extract(request).flatMap(this::principalOf);
    }

    private Optional<Principal> principalOf(String token) {
        JWTClaimsSet claims;
        try {
            claims = processor.process(token, null);
        } catch (ParseException | BadJOSEException | JOSEException ex) {
            return Optional.empty();
        }
        if (!claimsMatch(claims)) {
            return Optional.empty();
        }
        FoundationSecurityProperties.Claims mapping = properties.getClaims();
        return Optional.of(new Principal(
                claim(claims, mapping.getUserId()),
                claim(claims, mapping.getPersonId()),
                claim(claims, mapping.getAccountId()),
                claim(claims, mapping.getEmployeeNo()),
                claim(claims, mapping.getDisplayName()),
                claim(claims, mapping.getPrimaryOrgUnit()),
                claim(claims, mapping.getAppCode())));
    }

    private boolean claimsMatch(JWTClaimsSet claims) {
        String issuer = properties.getIssuer();
        if (issuer != null && !issuer.isBlank() && !issuer.equals(claims.getIssuer())) {
            return false;
        }
        String audience = properties.getAudience();
        if (audience != null && !audience.isBlank() && !claims.getAudience().contains(audience)) {
            return false;
        }
        return true;
    }

    private static String claim(JWTClaimsSet claims, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return claims.getStringClaim(name);
        } catch (ParseException ex) {
            return null;
        }
    }
}
