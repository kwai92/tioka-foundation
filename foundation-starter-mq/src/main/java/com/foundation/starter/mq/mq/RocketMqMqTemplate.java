package com.foundation.starter.mq.mq;

import com.foundation.starter.mq.config.FoundationMqProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;

/**
 * RocketMQ 投递实现（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>destination 形如 {@code topic:tag}（tag 为空则只发 topic），topic 由 {@code topic-prefix} 补齐。
 */
public class RocketMqMqTemplate implements MqTemplate {

    private final RocketMQTemplate rocketMQTemplate;
    private final FoundationMqProperties properties;

    public RocketMqMqTemplate(RocketMQTemplate rocketMQTemplate, FoundationMqProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    @Override
    public void send(String topic, String tag, String body) {
        String base = properties.getTopicPrefix() + topic;
        String destination = tag == null || tag.isBlank() ? base : base + ":" + tag;
        rocketMQTemplate.send(destination, MessageBuilder.withPayload(body).build());
    }
}
