package com.foundation.auth.domain;

/**
 * 登录方式领域对象（F-04 BAS-15 §2 登录方式）。
 *
 * @param id          记录 ID（{@code alm_…}）
 * @param modeCode    方式编码（{@link LoginModes}）
 * @param name        显示名
 * @param enabled     是否启用
 * @param sortOrder   排序号（升序）
 * @param complex     是否复杂项（引导到 Keycloak 控制台）
 * @param consoleHint 控制台引导说明
 */
public record LoginMode(String id, String modeCode, String name, boolean enabled, int sortOrder,
                        boolean complex, String consoleHint) {
}
