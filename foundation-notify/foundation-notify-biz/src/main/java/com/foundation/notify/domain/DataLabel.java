package com.foundation.notify.domain;

/**
 * 数据标签（ADR-040）：密级由低到高，枚举序号即密级顺序。
 *
 * <p>渠道外发许可用 {@link #within(DataLabel)} 判定：消息密级不高于渠道许可才允许外发。
 */
public enum DataLabel {

    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    SECRET;

    /** 宽松解析；空值与无法识别时取 {@link #INTERNAL}（与库默认一致）。 */
    public static DataLabel of(String value) {
        if (value == null || value.isBlank()) {
            return INTERNAL;
        }
        for (DataLabel label : values()) {
            if (label.name().equalsIgnoreCase(value.trim())) {
                return label;
            }
        }
        return INTERNAL;
    }

    /** 本标签是否不高于给定许可（许可即上限）。 */
    public boolean within(DataLabel clearance) {
        return this.ordinal() <= clearance.ordinal();
    }
}
