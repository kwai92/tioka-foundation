package com.foundation.calendar.api.event;

/**
 * {@code fdn.calendar.changed} 事件契约（F-09 BAS-18 §18.1）：日历逐日类型新增 / 覆盖 / 删除、官方日历导入。
 *
 * <p>经 {@code fdn_outbox} → 主题 {@code fdn-events} 投递；发布时机与业务落库同事务（S15 §14.2）。
 */
public final class CalendarChangedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.calendar.changed";

    /** 维护某日类型。 */
    public static final String CHANGE_UPSERT_DAY = "UPSERT_DAY";

    /** 导入官方日历。 */
    public static final String CHANGE_IMPORTED = "IMPORTED";

    private CalendarChangedEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型）。 */
    public static CalendarEventPayload payload(String calendarId, long version, String changeType) {
        return new CalendarEventPayload(calendarId, version, changeType);
    }
}
