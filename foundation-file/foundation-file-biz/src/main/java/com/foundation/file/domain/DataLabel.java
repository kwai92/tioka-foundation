package com.foundation.file.domain;

/**
 * 数据标签（ADR-040）：密级由低到高，枚举序号即密级顺序。
 *
 * <p>与 {@code com.foundation.notify.domain.DataLabel} 同口径（跨模块只共享 -api，
 * 该枚举为各模块自身领域概念，故各持一份）；高密级文件的下载 / 外发受 ADR-040 去向许可约束。
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
