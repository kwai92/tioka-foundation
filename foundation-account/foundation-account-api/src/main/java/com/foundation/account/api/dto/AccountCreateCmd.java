package com.foundation.account.api.dto;

/**
 * 开通个人账号入参（F-04 §7 {@code POST /api/accounts}）。
 *
 * @param personId  关联人员 ID（BAS-02）
 * @param loginName 登录名；为空时按 {@code foundation.account.provision.login-name-rule} 生成
 * @param operator  操作人（审计用）
 */
public record AccountCreateCmd(String personId, String loginName, String operator) {
}
