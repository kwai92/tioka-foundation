package com.foundation.sync.spi;

/**
 * 增量方式（F-08 BAS-11 §2）：来源在能力声明里说明自己能提供哪种增量（ADR-078 第 2 条）。
 */
public enum IncrementalMode {

    /** 无增量能力，只能全量快照。 */
    FULL_ONLY(false),

    /** 按时间戳增量（{@code getUpdated(since)}）。 */
    TIMESTAMP(true),

    /** 按变更号增量（如 LDAP {@code uSNChanged}）。 */
    CHANGE_NUMBER(true),

    /** 事件回调驱动（如飞书通讯录事件订阅），配合定时全量兜底。 */
    EVENT_CALLBACK(true);

    private final boolean incremental;

    IncrementalMode(boolean incremental) {
        this.incremental = incremental;
    }

    /** 是否具备真正的增量拉取能力。 */
    public boolean supportsIncremental() {
        return incremental;
    }

    /** 宽松解析：未知取值返回 {@code null}。 */
    public static IncrementalMode of(String value) {
        if (value == null) {
            return null;
        }
        for (IncrementalMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return null;
    }
}
