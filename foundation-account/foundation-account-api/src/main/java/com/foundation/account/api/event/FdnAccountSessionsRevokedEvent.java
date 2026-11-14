package com.foundation.account.api.event;

/** {@code fdn.account.sessions_revoked} 事件契约（F-04 §7）：账号会话被强制吊销。 */
public final class FdnAccountSessionsRevokedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.account.sessions_revoked";

    /** 变化类型：会话吊销。 */
    public static final String CHANGE_SESSIONS_REVOKED = "SESSIONS_REVOKED";

    private FdnAccountSessionsRevokedEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型）。 */
    public static AccountEventPayload payload(String accountId, long version) {
        return new AccountEventPayload(accountId, version, CHANGE_SESSIONS_REVOKED);
    }
}
