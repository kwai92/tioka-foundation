package com.foundation.auth.api;

/**
 * 登录方式视图（F-04 BAS-15 §2 登录方式）。
 *
 * @param modeCode    方式编码：{@code password}/{@code oidc}/{@code ldap}/{@code saml}/{@code im-qr}
 * @param name        显示名
 * @param enabled     是否启用
 * @param sortOrder   排序号（升序）
 * @param complex     是否复杂项（复杂项引导到 Keycloak 控制台完成）
 * @param consoleHint 控制台引导说明（复杂项）
 */
public record LoginModeView(String modeCode, String name, boolean enabled, int sortOrder,
                            boolean complex, String consoleHint) {
}
