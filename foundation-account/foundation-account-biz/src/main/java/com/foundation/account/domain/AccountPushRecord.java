package com.foundation.account.domain;

import java.time.Instant;

/**
 * 账号写操作待发记录（F-04 §5；{@code fdn_account_push}）。
 *
 * <p>明文口令与重置链接不落库、不进日志：{@code payloadJson} 只含非敏感推送参数。
 *
 * @param id          主键（{@code apu_…}）
 * @param accountId   目标账号 ID
 * @param action      推送动作
 * @param payloadJson 推送参数（不含口令与重置链接），可空
 * @param status      推送状态
 * @param attempts    已尝试次数
 * @param lastError   最近一次失败原因，可空
 * @param nextRetryAt 下次重试时间，可空表示立即
 */
public record AccountPushRecord(String id,
                                String accountId,
                                PushAction action,
                                String payloadJson,
                                PushStatus status,
                                int attempts,
                                String lastError,
                                Instant nextRetryAt) {

    /** 新建待推送记录。 */
    public static AccountPushRecord pending(String id, String accountId, PushAction action, String payloadJson) {
        return new AccountPushRecord(id, accountId, action, payloadJson, PushStatus.PENDING, 0, null, null);
    }

    /** 是否到期可推送（{@code nextRetryAt} 为空或已到）。 */
    public boolean due(Instant now) {
        return nextRetryAt == null || !nextRetryAt.isAfter(now);
    }

    /** 标记为已推送。 */
    public AccountPushRecord sent() {
        return new AccountPushRecord(id, accountId, action, payloadJson, PushStatus.SENT,
                attempts + 1, null, null);
    }

    /**
     * 标记为推送失败。
     *
     * @param error        失败原因（须脱敏，不得含口令）
     * @param nextRetryAt  下次重试时间；为 {@code null} 表示不再自动重试（终态 {@code FAILED}）
     */
    public AccountPushRecord failed(String error, Instant nextRetryAt) {
        return new AccountPushRecord(id, accountId, action, payloadJson,
                nextRetryAt == null ? PushStatus.FAILED : PushStatus.PENDING,
                attempts + 1, error, nextRetryAt);
    }
}
