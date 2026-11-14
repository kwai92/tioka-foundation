package com.foundation.account.infrastructure;

import com.foundation.account.domain.AccountPushRecord;

import java.time.Instant;
import java.util.List;

/**
 * 账号待发记录仓储（F-04 §5，MyBatis-Plus）。
 *
 * <p>写操作先落 Foundation 库、再推送 Keycloak；推送失败进本表重试，对账任务兜底修复。
 */
public interface AccountPushRepository {

    void insert(AccountPushRecord record);

    /**
     * 取到期可推送的待发记录（{@code status=PENDING} 且 {@code next_retry_at} 为空或已到），按入队顺序升序。
     *
     * @param now   当前时间
     * @param limit 单批上限
     */
    List<AccountPushRecord> findDue(Instant now, int limit);

    /** 待推送（{@code status=PENDING}）条数。 */
    long countPending();

    /** 标记为已推送。 */
    void markSent(String id);

    /**
     * 标记为推送失败。
     *
     * @param nextRetryAt 下次重试时间；为 {@code null} 表示不再自动重试（终态 {@code FAILED}）
     */
    void markFailed(String id, String error, Instant nextRetryAt);
}
