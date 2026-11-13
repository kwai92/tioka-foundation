package com.foundation.common.api.keycloak;

/** OIDC 授权类型常量（Keycloak {@code grant_types} / 客户端流程开关映射用）。 */
public final class OidcGrantTypes {

    public static final String AUTHORIZATION_CODE = "authorization_code";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String CLIENT_CREDENTIALS = "client_credentials";
    public static final String IMPLICIT = "implicit";
    public static final String PASSWORD = "password";

    private OidcGrantTypes() {
    }
}
