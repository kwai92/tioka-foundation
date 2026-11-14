package com.foundation.account.infrastructure;

import com.foundation.account.api.event.AccountEventPayload;
import com.foundation.account.api.event.FdnAccountSessionsRevokedEvent;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;

/**
 * 账号域事件 outbox 写入（F-04 §7、BAS-14 §2.4）。
 *
 * <p>经 {@code fdn_outbox} → 主题 {@code fdn-events} 投递；事件体只带 {@code id}、{@code version}、
 * {@code changeType}，不含敏感字段。须由调用方置于业务事务内。
 */
public class AccountEventOutbox {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public AccountEventOutbox(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** {@code fdn.account.sessions_revoked}：停用、强制下线、单点注销。 */
    public void sessionsRevoked(String accountId, long version) {
        publish(FdnAccountSessionsRevokedEvent.EVENT_TYPE, FdnAccountSessionsRevokedEvent.payload(accountId, version));
    }

    private void publish(String eventType, AccountEventPayload payload) {
        outbox.append(events.getTopic(), eventType, payload.id(), payload.toMap());
    }
}
