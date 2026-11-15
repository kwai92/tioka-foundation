package com.foundation.starter.mq.event;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 领域事件（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>事件名遵循 S15 §14.2：{@code fdn.<聚合>.<动作>}，如 {@code fdn.dict.changed}；
 * payload 只带 ID、版本与变化类型，body 为 null 时退化为空 Map。
 */
public record DomainEvent(String topic, String eventType, String aggregate, Map<String, Object> payload,
                          int schemaVersion) {

    /** 事件契约版本（ADP-02 / 36 号规划 §6）：加字段为兼容变更，删改语义才升主版本。 */
    public static final int DEFAULT_SCHEMA_VERSION = 1;

    /** 事件名格式（S15 §14.2）：必须以 {@code fdn.} 起头。 */
    private static final Pattern EVENT_TYPE = Pattern.compile("^fdn\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_.]*$");

    /** 兼容构造：既有四参数调用点零改动，契约版本取 v1。 */
    public DomainEvent(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        this(topic, eventType, aggregate, payload, DEFAULT_SCHEMA_VERSION);
    }

    public DomainEvent {
        requireText(topic, "topic");
        requireText(eventType, "eventType");
        requireText(aggregate, "aggregate");
        if (!EVENT_TYPE.matcher(eventType).matches()) {
            throw new IllegalArgumentException("非法事件名（S15 §14.2 应为 fdn.<聚合>.<动作>）: " + eventType);
        }
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("schemaVersion 必须为正整数");
        }
    }

    /** 便捷工厂：与 {@link com.foundation.common.api.event.OutboxAppender} 的四参数对齐。 */
    public static DomainEvent of(String topic, String eventType, String aggregate, Map<String, Object> payload) {
        return new DomainEvent(topic, eventType, aggregate, payload);
    }

    /** 显式契约版本工厂（ADP-02）。 */
    public static DomainEvent of(String topic, String eventType, String aggregate,
                                 Map<String, Object> payload, int schemaVersion) {
        return new DomainEvent(topic, eventType, aggregate, payload, schemaVersion);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
