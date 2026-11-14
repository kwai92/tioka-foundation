package com.foundation.sync.infrastructure;

import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.org.api.event.OrgChangedEvent;
import com.foundation.org.api.event.OrgEventPayload;
import com.foundation.org.api.event.PersonCreatedEvent;
import com.foundation.org.api.event.PersonDisabledEvent;
import com.foundation.org.api.event.PersonUpdatedEvent;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.event.FdnSyncCompletedEvent;
import com.foundation.sync.api.event.FdnSyncFailedEvent;
import com.foundation.sync.api.event.FdnSyncPausedEvent;
import com.foundation.sync.domain.LandedChange;

import java.util.Map;

/**
 * 同步域事件 outbox 写入（F-08 BAS-11 §10）。
 *
 * <p>同步事件（{@code fdn.sync.completed} / {@code fdn.sync.paused} / {@code fdn.sync.failed}）经
 * {@code fdn_outbox} → 主题 {@code fdn-events} 投递；主数据落地后另复用组织域事件
 * （{@code fdn.org.changed} / {@code fdn.person.created} / {@code updated} / {@code disabled}），
 * 让下游（账号、通知、审计）无需区分「人工维护」与「同步落地」。须由调用方置于业务事务内。
 */
public class SyncEventOutbox {

    private final OutboxAppender outbox;
    private final EventProperties events;

    public SyncEventOutbox(OutboxAppender outbox, EventProperties events) {
        this.outbox = outbox;
        this.events = events;
    }

    /** {@code fdn.sync.completed}：同步（含对账）成功或部分成功。 */
    public void syncCompleted(String taskId, String sourceCode, Map<String, Integer> counts,
                              boolean capabilityMissing) {
        publish(FdnSyncCompletedEvent.EVENT_TYPE, taskId,
                FdnSyncCompletedEvent.payload(taskId, sourceCode, counts, capabilityMissing));
    }

    /** {@code fdn.sync.paused}：差异超阈值暂停待确认。 */
    public void syncPaused(String taskId, String sourceCode, SyncPauseReason pauseReason,
                           Map<String, Integer> counts) {
        publish(FdnSyncPausedEvent.EVENT_TYPE, taskId,
                FdnSyncPausedEvent.payload(taskId, sourceCode, pauseReason, counts));
    }

    /** {@code fdn.sync.failed}：同步失败。 */
    public void syncFailed(String taskId, String sourceCode, String errorCode) {
        publish(FdnSyncFailedEvent.EVENT_TYPE, taskId,
                FdnSyncFailedEvent.payload(taskId, sourceCode, errorCode));
    }

    /**
     * 主数据落地后复用组织域事件（{@code fdn.org.changed} / {@code fdn.person.*}）。
     *
     * <p>岗位 / 任职 / 职级 / 外部身份不产生组织域事件，由同步报告承载。
     */
    public void landed(LandedChange change) {
        if (change == null || change.objectType() == null) {
            return;
        }
        switch (change.objectType()) {
            case ORG_UNIT -> publish(OrgChangedEvent.EVENT_TYPE, change.localId(),
                    OrgChangedEvent.payload(change.localId(), change.version(), change.changeType()));
            case PERSON -> landedPerson(change);
            default -> {
                // 其余对象无对应组织域事件。
            }
        }
    }

    private void landedPerson(LandedChange change) {
        String changeType = change.changeType() == null ? "" : change.changeType();
        switch (changeType) {
            case LandedChange.CREATED -> publish(PersonCreatedEvent.EVENT_TYPE, change.localId(),
                    PersonCreatedEvent.payload(change.localId(), change.version()));
            case LandedChange.DISABLED -> publish(PersonDisabledEvent.EVENT_TYPE, change.localId(),
                    PersonDisabledEvent.payload(change.localId(), change.version()));
            default -> publish(PersonUpdatedEvent.EVENT_TYPE, change.localId(),
                    PersonUpdatedEvent.payload(change.localId(), change.version()));
        }
    }

    private void publish(String eventType, String aggregate, Map<String, Object> payload) {
        outbox.append(events.getTopic(), eventType, aggregate, payload);
    }

    private void publish(String eventType, String aggregate, OrgEventPayload payload) {
        outbox.append(events.getTopic(), eventType, aggregate, payload.toMap());
    }
}
