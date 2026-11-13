package com.foundation.app.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.api.keycloak.OidcGrantTypes;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端登记规则值对象（F-05 BAS-04 §自动开通与密钥轮换）。
 *
 * <p>登记即校验，规则：
 * <ul>
 *   <li>授权类型非空、取值受支持、去重保序；</li>
 *   <li>回调地址若给出必须为绝对 {@code http}/{@code https} 地址；</li>
 *   <li>{@code authorization_code} / {@code implicit} 流程必须给出回调地址；</li>
 *   <li>{@code client_credentials} 是服务账号专用流程，不能与其他流程混用（S15 §8.2）。</li>
 * </ul>
 */
public record ClientRegistration(List<String> grantTypes, List<String> redirectUris) {

    public static final List<String> DEFAULT_GRANT_TYPES =
            List.of(OidcGrantTypes.AUTHORIZATION_CODE, OidcGrantTypes.REFRESH_TOKEN);

    private static final Set<String> SUPPORTED = Set.of(
            OidcGrantTypes.AUTHORIZATION_CODE, OidcGrantTypes.REFRESH_TOKEN,
            OidcGrantTypes.CLIENT_CREDENTIALS, OidcGrantTypes.IMPLICIT, OidcGrantTypes.PASSWORD);

    private static final Set<String> FLOWS_REQUIRING_REDIRECT =
            Set.of(OidcGrantTypes.AUTHORIZATION_CODE, OidcGrantTypes.IMPLICIT);

    /** 校验并归一化；授权类型为空时取默认 {@code authorization_code,refresh_token}。 */
    public static ClientRegistration of(List<String> grantTypes, List<String> redirectUris) {
        List<String> grants = normalizeGrants(grantTypes);
        List<String> redirects = normalizeRedirects(redirectUris);

        if (grants.contains(OidcGrantTypes.CLIENT_CREDENTIALS) && grants.size() > 1) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "服务账号客户端只用 client_credentials，不能叠加其他授权类型：" + grants);
        }
        if (grants.stream().anyMatch(FLOWS_REQUIRING_REDIRECT::contains) && redirects.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "authorization_code/implicit 流程必须给出回调地址");
        }
        return new ClientRegistration(grants, redirects);
    }

    /** 服务账号客户端：只用 {@code client_credentials}，无回调地址。 */
    public static ClientRegistration serviceAccount() {
        return new ClientRegistration(List.of(OidcGrantTypes.CLIENT_CREDENTIALS), List.of());
    }

    private static List<String> normalizeGrants(List<String> grantTypes) {
        if (grantTypes == null || grantTypes.isEmpty()) {
            return DEFAULT_GRANT_TYPES;
        }
        List<String> grants = new ArrayList<>(new LinkedHashSet<>(grantTypes));
        for (String grant : grants) {
            if (grant == null || !SUPPORTED.contains(grant.trim())) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        "不支持的授权类型（可选 authorization_code/refresh_token/client_credentials/implicit/password）："
                                + grant);
            }
        }
        return grants;
    }

    private static List<String> normalizeRedirects(List<String> redirectUris) {
        if (redirectUris == null || redirectUris.isEmpty()) {
            return List.of();
        }
        List<String> redirects = new ArrayList<>(new LinkedHashSet<>(redirectUris));
        for (String uri : redirects) {
            if (!isAbsoluteHttpUri(uri)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "回调地址需为绝对 http/https 地址：" + uri);
            }
        }
        return redirects;
    }

    private static boolean isAbsoluteHttpUri(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute()
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
