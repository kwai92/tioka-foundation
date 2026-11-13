package com.foundation.org.api.event;

/** {@code fdn.person.created} 事件契约（F-03 §6）：人员新增，含数字员工上岗。 */
public final class PersonCreatedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.person.created";

    /** 变化类型：新增。 */
    public static final String CHANGE_CREATED = "CREATED";

    private PersonCreatedEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型）。 */
    public static OrgEventPayload payload(String personId, long version) {
        return new OrgEventPayload(personId, version, CHANGE_CREATED);
    }
}
