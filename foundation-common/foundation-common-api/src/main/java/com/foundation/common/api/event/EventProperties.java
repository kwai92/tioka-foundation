package com.foundation.common.api.event;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 事件配置（F-02 BAS-09 §配置项）。 */
@ConfigurationProperties(prefix = "foundation.event")
public class EventProperties {

    /** outbox 投递主题（与 BAS-24 mq starter 共用）。 */
    private String topic = "fdn-events";

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }
}
