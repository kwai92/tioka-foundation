package com.foundation.auth.api;

/**
 * 单次下发项的结果（F-04 BAS-15 §4：下发失败列出失败项与 Keycloak 返回，不静默成功）。
 *
 * @param item   下发项标识（如 {@code password} / {@code session} / {@code login-mode:oidc} / {@code required-actions}）
 * @param success 是否成功
 * @param detail  明细（失败时为 Keycloak 返回或异常信息）
 */
public record AuthApplyItem(String item, boolean success, String detail) {
}
