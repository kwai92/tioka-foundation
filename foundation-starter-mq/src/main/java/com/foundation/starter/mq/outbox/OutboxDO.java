package com.foundation.starter.mq.outbox;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code fdn_outbox} 行映射（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>字段与 V1__foundation_schema.sql 的 {@code fdn_outbox} 一一对应；{@code payload} 为
 * JSON 文本（DB 列 {@code jsonb}，写入须在 XML 里 {@code CAST(... AS jsonb)}）。
 * 本表自带 {@code create_at}/{@code status}/{@code sent_at} 语义，不继承审计基类 {@code BaseDO}。
 */
@TableName("fdn_outbox")
public class OutboxDO {

    /** 主键，{@code evt_{ulid}}（列宽 VARCHAR(32)）。 */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    private String topic;

    private String eventType;

    private String aggregate;

    /** JSON 文本（DB 列 {@code jsonb}）。 */
    private String payload;

    /** {@code NEW} / {@code SENT} / {@code DEAD}。 */
    private String status;

    private Integer retryCount;

    /** 创建时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    private LocalDateTime createAt;

    /** 投递时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    private LocalDateTime sentAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getAggregate() {
        return aggregate;
    }

    public void setAggregate(String aggregate) {
        this.aggregate = aggregate;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public LocalDateTime getCreateAt() {
        return createAt;
    }

    public void setCreateAt(LocalDateTime createAt) {
        this.createAt = createAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
