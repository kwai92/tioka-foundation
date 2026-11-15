package com.foundation.starter.mq.mq;

/**
 * 消息发送门面（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>实现负责拼接 {@code foundation.mq.topic-prefix}；无 RocketMQ 时退化为日志投递。
 */
public interface MqTemplate {

    /**
     * 发送一条消息。
     *
     * @param topic 逻辑主题（实现补 {@code topic-prefix}）
     * @param tag   RocketMQ tag；为空则只发 topic
     * @param body  消息体（JSON 文本）
     */
    void send(String topic, String tag, String body);
}
