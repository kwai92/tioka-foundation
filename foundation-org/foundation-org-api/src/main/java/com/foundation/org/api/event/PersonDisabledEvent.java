package com.foundation.org.api.event;

/** {@code fdn.person.disabled} 事件契约（F-03 §6）：人员停用、离职、数字员工下岗。 */
public final class PersonDisabledEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.person.disabled";

    /** 变化类型：停用。 */
    public static final String CHANGE_DISABLED = "DISABLED";

    /** 变化类型：离职（F-04 §BAS-14 载荷的 {@code reason=离职}）。 */
    public static final String CHANGE_LEFT = "LEFT";

    private PersonDisabledEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型），变化类型为停用。 */
    public static OrgEventPayload payload(String personId, long version) {
        return payload(personId, version, CHANGE_DISABLED);
    }

    /**
     * 构造事件体（只带 ID、版本与变化类型）。
     *
     * @param changeType 变化类型：{@link #CHANGE_DISABLED} / {@link #CHANGE_LEFT}
     */
    public static OrgEventPayload payload(String personId, long version, String changeType) {
        return new OrgEventPayload(personId, version, changeType);
    }
}
