package com.foundation.auth.api;

/**
 * 修改登录方式的命令（F-04 BAS-15 §4 {@code PUT /api/auth/login-modes}）。
 *
 * <p>字段可空：为空表示沿用现有值；{@code password} 为本地凭证，启用 / 停用不下发身份代理。
 *
 * @param modeCode  方式编码
 * @param enabled   是否启用
 * @param sortOrder 排序号
 */
public record LoginModeCmd(String modeCode, Boolean enabled, Integer sortOrder) {
}
