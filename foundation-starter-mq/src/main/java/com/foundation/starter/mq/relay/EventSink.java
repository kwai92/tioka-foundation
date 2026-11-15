package com.foundation.starter.mq.relay;

import com.foundation.starter.mq.outbox.OutboxEntry;

/**
 * 事件投递通道 SPI（ADP-02 / 36 号规划 §3 P3「依赖可替换」）。
 *
 * <p>MQ 通道由 {@code MqTemplate} 承担，本接口用于追加并列通道（内置 Webhook 实现，
 * 业务项目也可自行注册 Kafka、SNS、消息队列适配等）。中继按顺序调用：MQ → 各 {@link EventSink}，
 * 任一通道失败整条转重试/死信，因此消费方必须按 {@code eventType + deliveryId} 幂等。
 *
 * <p>实现应把受检异常包装为 {@link RuntimeException}，避免中继签名扩散。
 */
public interface EventSink {

    /** 通道名（日志与诊断用）。 */
    String name();

    /** 投递一条待发送事件；非 2xx 或网络异常时抛运行时异常以触发 outbox 重试。 */
    void deliver(OutboxEntry entry);
}
