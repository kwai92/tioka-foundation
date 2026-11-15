package com.foundation.starter.mq.consumer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * {@code fdn_consumed} 行映射（F-02 BAS-24 §foundation-starter-mq）：消费幂等去重表。
 *
 * <p>该表为复合主键 {@code (consumer, message_id)}，插入走 XML 显式 SQL
 * （{@code INSERT ... ON CONFLICT DO NOTHING}）。约定 DDL：
 * <pre>
 * CREATE TABLE fdn_consumed (
 *     consumer   VARCHAR(64)  NOT NULL,
 *     message_id VARCHAR(128) NOT NULL,
 *     consumed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 *     PRIMARY KEY (consumer, message_id)
 * );
 * </pre>
 */
@TableName("fdn_consumed")
public class ConsumedMessageDO {

    /** 复合主键之一；MyBatis-Plus 单主键约定，另一字段作为普通列。 */
    @TableId(value = "consumer", type = IdType.INPUT)
    private String consumer;

    @TableField("message_id")
    private String messageId;

    /** 消费时间（UTC 语义，列 {@code TIMESTAMPTZ}）。 */
    @TableField("consumed_at")
    private LocalDateTime consumedAt;

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }
}
