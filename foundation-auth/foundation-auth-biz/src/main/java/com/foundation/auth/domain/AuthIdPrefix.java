package com.foundation.auth.domain;

/**
 * 登录策略主键前缀（00-施工总纲 §2.2：{@code {前缀}_{ulid}}，列宽 {@code VARCHAR(32)}）。
 */
public final class AuthIdPrefix {

    /** 登录策略（单行有效）：{@code pol_{ulid}}。 */
    public static final String POLICY = "pol";

    /** 登录方式：{@code alm_{ulid}}。 */
    public static final String LOGIN_MODE = "alm";

    private AuthIdPrefix() {
    }
}
