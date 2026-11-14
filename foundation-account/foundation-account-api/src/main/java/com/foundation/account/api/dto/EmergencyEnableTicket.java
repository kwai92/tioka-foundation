package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 应急管理员启用申请凭据（F-04 §7 双人确认第一步）。
 *
 * @param requestId  申请 ID（第二步确认时回传）
 * @param accountId  应急管理员账号 ID
 * @param expiresAt  申请失效时间（超时需重新发起）
 */
public record EmergencyEnableTicket(String requestId, String accountId, Instant expiresAt) {
}
