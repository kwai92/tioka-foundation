package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 离职联动结果（F-04 BAS-14 §1 动作链、§3 验收口径）。
 *
 * @param personId         人员 ID（{@code per_…}）
 * @param accountId        个人账号 ID（{@code acc_…}），该人员无个人账号时为空
 * @param keycloakUserId   Keycloak 用户 ID，无账号或未推送时为空
 * @param sessionsRevoked  是否已吊销会话（用于 §3 的 1 分钟口径核验）
 * @param revokedAt        会话吊销时间，未吊销时为空
 * @param handoverCount    本次进入交接待办的条数
 */
public record OffboardingResult(String personId,
                                String accountId,
                                String keycloakUserId,
                                boolean sessionsRevoked,
                                Instant revokedAt,
                                int handoverCount) {
}
