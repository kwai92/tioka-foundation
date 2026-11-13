package com.foundation.authz.api;

import java.time.Instant;

/**
 * 授权变更事件（F-06 BAS-07 §事件 {@code fdn.authz.changed}）。
 *
 * <p>经 outbox → RocketMQ 主题 {@code fdn-events} 投递；事件体只带 ID、版本与变化类型，
 * 不带敏感字段（订阅方需要详情时回查快照接口）。要求授权变更后 5 秒内发出，
 * {@code snapshotVersion} 按 {@code (人员, 应用)} 单调递增。
 */
public record AuthzChangedEvent(String eventId,
                                String personId,
                                String appCode,
                                long snapshotVersion,
                                String changeType,
                                Instant occurredAt) {

    /** outbox 事件名（S15 §14.2）。 */
    public static final String EVENT_TYPE = "fdn.authz.changed";

    /** 变化类型：授权重算（本期唯一类型）。 */
    public static final String CHANGE_TYPE_RECOMPUTED = "AUTHZ_RECOMPUTED";
}
