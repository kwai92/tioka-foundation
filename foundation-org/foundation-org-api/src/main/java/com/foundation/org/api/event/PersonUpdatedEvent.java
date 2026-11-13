package com.foundation.org.api.event;

/** {@code fdn.person.updated} 事件契约（F-03 §6）：人员修改、调岗（任职变化）。 */
public final class PersonUpdatedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.person.updated";

    /** 变化类型：修改。 */
    public static final String CHANGE_UPDATED = "UPDATED";

    private PersonUpdatedEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型）。 */
    public static OrgEventPayload payload(String personId, long version) {
        return new OrgEventPayload(personId, version, CHANGE_UPDATED);
    }
}
