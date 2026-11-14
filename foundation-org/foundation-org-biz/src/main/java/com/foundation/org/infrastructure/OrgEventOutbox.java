package com.foundation.org.infrastructure;

import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.org.api.event.OrgChangedEvent;
import com.foundation.org.api.event.OrgEventPayload;
import com.foundation.org.api.event.PersonCreatedEvent;
import com.foundation.org.api.event.PersonDisabledEvent;
import com.foundation.org.api.event.PersonUpdatedEvent;

/**
 * 组织域事件 outbox 写入（F-03 §6、§7.3）。
 *
 * <p>经 {@code fdn_outbox} → 主题 {@code fdn-events} 投递；事件体只带 {@code id}、{@code version}、
 * {@code changeType}，不含敏感字段。须由调用方置于业务事务内。
 */
public class OrgEventOutbox {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public OrgEventOutbox(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** {@code fdn.org.changed}：组织单元新增、修改、移动、停用。 */
    public void orgChanged(String orgUnitId, long version, String changeType) {
        publish(OrgChangedEvent.EVENT_TYPE, OrgChangedEvent.payload(orgUnitId, version, changeType));
    }

    /** {@code fdn.person.created}：人员新增（含数字员工上岗）。 */
    public void personCreated(String personId, long version) {
        publish(PersonCreatedEvent.EVENT_TYPE, PersonCreatedEvent.payload(personId, version));
    }

    /** {@code fdn.person.updated}：人员修改、调岗。 */
    public void personUpdated(String personId, long version) {
        publish(PersonUpdatedEvent.EVENT_TYPE, PersonUpdatedEvent.payload(personId, version));
    }

    /** {@code fdn.person.disabled}：人员停用、离职、数字员工下岗。 */
    public void personDisabled(String personId, long version) {
        personDisabled(personId, version, PersonDisabledEvent.CHANGE_DISABLED);
    }

    /**
     * {@code fdn.person.disabled}：指定变化类型。
     *
     * @param changeType {@code DISABLED}（停用 / 下岗）或 {@code LEFT}（离职，F-04 BAS-14）
     */
    public void personDisabled(String personId, long version, String changeType) {
        publish(PersonDisabledEvent.EVENT_TYPE, PersonDisabledEvent.payload(personId, version, changeType));
    }

    private void publish(String eventType, OrgEventPayload payload) {
        outbox.append(events.getTopic(), eventType, payload.id(), payload.toMap());
    }
}
