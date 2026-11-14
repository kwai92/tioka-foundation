package com.foundation.notify.api;

/** 消息优先级（F-09 BAS-16 §数据模型 {@code fdn_message.priority}）。 */
public enum MessagePriority {

    LOW,
    NORMAL,
    HIGH;

    /** 宽松解析；空值与无法识别时取 {@link #NORMAL}。 */
    public static MessagePriority of(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        for (MessagePriority priority : values()) {
            if (priority.name().equalsIgnoreCase(value.trim())) {
                return priority;
            }
        }
        return NORMAL;
    }
}
