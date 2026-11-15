package com.foundation.starter.mq.mq;

import com.foundation.starter.mq.config.FoundationMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志投递实现（F-02 BAS-24 §foundation-starter-mq）：无 RocketMQ 时的默认 {@link MqTemplate}。
 *
 * <p>只记录 topic/tag/长度与摘要，不抛异常，便于本地与单测运行。
 */
public class LoggingMqTemplate implements MqTemplate {

    private static final Logger log = LoggerFactory.getLogger(LoggingMqTemplate.class);

    private static final int SUMMARY_LIMIT = 128;

    private final FoundationMqProperties properties;

    public LoggingMqTemplate(FoundationMqProperties properties) {
        this.properties = properties;
    }

    @Override
    public void send(String topic, String tag, String body) {
        String destination = destination(topic, tag);
        int length = body == null ? 0 : body.length();
        log.info("日志投递（无 RocketMQ）: destination={}, length={}, body={}", destination, length, summary(body));
    }

    private String destination(String topic, String tag) {
        String base = properties.getTopicPrefix() + topic;
        return tag == null || tag.isBlank() ? base : base + ":" + tag;
    }

    private String summary(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= SUMMARY_LIMIT ? body : body.substring(0, SUMMARY_LIMIT) + "...";
    }
}
