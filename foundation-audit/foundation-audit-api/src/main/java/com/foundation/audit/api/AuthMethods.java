package com.foundation.audit.api;

/**
 * 认证方式口径（F-09 BAS-10 §登录日志）。
 *
 * <p>Keycloak 事件里的 {@code details.auth_method} 取值不统一（如 {@code openid-connect}、{@code password}），
 * 统一经 {@link #normalize(String)} 归一为下表常量，便于登录日志统计与展示。
 */
public final class AuthMethods {

    /** 口令登录。 */
    public static final String PASSWORD = "password";
    /** Keycloak OIDC 登录。 */
    public static final String OIDC_KEYCLOAK = "oidc:keycloak";
    /** SAML 联合登录。 */
    public static final String SAML = "saml";
    /** LDAP 登录。 */
    public static final String LDAP = "ldap";
    /** 飞书 IM 登录。 */
    public static final String IM_FEISHU = "im:feishu";

    private AuthMethods() {
    }

    /**
     * 归一认证方式：空 → {@link #PASSWORD}；已知值原样返回；形如 {@code oidc} 前缀的含冒号值原样返回；
     * 其余未知值原样小写返回。
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return PASSWORD;
        }
        String value = raw.trim();
        if (value.equalsIgnoreCase(PASSWORD) || value.equalsIgnoreCase(OIDC_KEYCLOAK)
                || value.equalsIgnoreCase(SAML) || value.equalsIgnoreCase(LDAP)
                || value.equalsIgnoreCase(IM_FEISHU)) {
            return value;
        }
        if (value.indexOf(':') >= 0 && value.toLowerCase().startsWith("oidc")) {
            return value;
        }
        return value.toLowerCase();
    }
}
