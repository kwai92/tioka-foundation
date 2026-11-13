package com.foundation.org.api.event;

/**
 * {@code fdn.org.changed} 事件契约（F-03 §6）：组织单元新增、修改、移动、停用。
 *
 * <p>经 {@code fdn_outbox} → 主题 {@code fdn-events} 投递；发布时机为
 * {@code TransactionSynchronization.afterCommit}，与业务落库同事务（总纲 §2.4）。
 */
public final class OrgChangedEvent {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.org.changed";

    /** 新增。 */
    public static final String CHANGE_CREATED = "CREATED";

    /** 修改。 */
    public static final String CHANGE_UPDATED = "UPDATED";

    /** 移动（换上级，{@code path} / {@code depth} 级联更新）。 */
    public static final String CHANGE_MOVED = "MOVED";

    /** 停用。 */
    public static final String CHANGE_DISABLED = "DISABLED";

    private OrgChangedEvent() {
    }

    /** 构造事件体（只带 ID、版本与变化类型）。 */
    public static OrgEventPayload payload(String orgUnitId, long version, String changeType) {
        return new OrgEventPayload(orgUnitId, version, changeType);
    }
}
