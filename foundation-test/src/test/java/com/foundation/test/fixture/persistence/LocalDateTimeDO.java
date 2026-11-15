package com.foundation.test.fixture.persistence;

import com.foundation.starter.data.MutableRecordDO;

import java.time.LocalDateTime;

/**
 * 规则 7 正例：DO 时间字段使用 LocalDateTime（UTC 语义）。
 */
public class LocalDateTimeDO extends MutableRecordDO {

    private LocalDateTime occurredAt;

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
