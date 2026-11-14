package com.foundation.account.api.dto;

/**
 * 重置口令结果（F-04 §5）：邮件重置或返回临时口令。
 *
 * <p>临时口令只在本结果中出现一次，**不落库、不进日志**；{@code temporaryPassword} 为空表示走邮件重置。
 *
 * @param emailSent          是否经 Keycloak 发送了重置邮件
 * @param temporaryPassword  一次性临时口令；邮件重置时为空
 */
public record AccountResetResult(boolean emailSent, String temporaryPassword) {
}
