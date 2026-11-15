package com.foundation.auth.domain;

import java.util.List;

/**
 * 登录方式编码常量与校验（F-04 BAS-15 §1 选型表）。
 *
 * <p>账号密码为 Keycloak 本地凭证；其余方式为身份代理（Identity Provider），
 * 其别名与 {@code modeCode} 一致。
 */
public final class LoginModes {

    /** 账号密码（本地凭证）。 */
    public static final String PASSWORD = "password";

    /** 企业 IdP（OIDC 身份代理）。 */
    public static final String OIDC = "oidc";

    /** LDAP / AD（用户联邦）。 */
    public static final String LDAP = "ldap";

    /** SAML 2.0（身份代理）。 */
    public static final String SAML = "saml";

    /** IM 扫码（飞书 / 企微 / 钉钉，OAuth 类身份代理）。 */
    public static final String IM_QR = "im-qr";

    private static final List<String> KNOWN = List.of(PASSWORD, OIDC, LDAP, SAML, IM_QR);

    private LoginModes() {
    }

    /** 是否为已知登录方式编码。 */
    public static boolean isKnown(String modeCode) {
        return modeCode != null && KNOWN.contains(modeCode);
    }

    /** 是否以身份代理实现（账号密码为本地凭证，无需身份代理）。 */
    public static boolean requiresIdentityProvider(String modeCode) {
        return !PASSWORD.equals(modeCode);
    }

    /** 已知编码列表（副本）。 */
    public static List<String> known() {
        return KNOWN;
    }
}
