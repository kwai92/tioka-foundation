package com.foundation.test.fixture.persistence;

import com.foundation.starter.data.MutableRecordDO;

import java.time.Instant;

/**
 * 规则 7 反例：DO 时间字段使用 Instant。
 */
public class InstantTimeFieldDO extends MutableRecordDO {

    private Instant createdAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
