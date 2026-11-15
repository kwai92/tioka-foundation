package com.foundation.starter.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 消息配置（F-02 BAS-24 §foundation-starter-mq：{@code foundation.mq.*}）。 */
@ConfigurationProperties(prefix = "foundation.mq")
public class FoundationMqProperties {

    /** 逻辑主题前缀（{@code foundation.mq.topic-prefix}）。 */
    private String topicPrefix = "fdn-";

    /** 生产组（{@code foundation.mq.producer-group}）。 */
    private String producerGroup = "foundation-producer";

    /** outbox 中继配置。 */
    private final Relay relay = new Relay();

    /** 消费配置。 */
    private final Consumer consumer = new Consumer();

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public Relay getRelay() {
        return relay;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    /** outbox 中继配置（{@code foundation.mq.relay.*}）。 */
    public static class Relay {

        /** 中继总开关；缺省关闭（本地/单测不启动调度）。 */
        private boolean enabled = false;

        /** 单批拉取条数。 */
        private int batchSize = 100;

        /** 轮询间隔。 */
        private Duration interval = Duration.ofSeconds(5);

        /** 最大重试次数，达到即置 {@code DEAD}。 */
        private int maxRetry = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public void setMaxRetry(int maxRetry) {
            this.maxRetry = maxRetry;
        }
    }

    /** 消费配置（{@code foundation.mq.consumer.*}）。 */
    public static class Consumer {

        /** 消费幂等记录的保留窗口（小时）。 */
        private int retentionHours = 24;

        /** 进程内幂等存储的最大条目数。 */
        private int maxMemoryEntries = 100000;

        public int getRetentionHours() {
            return retentionHours;
        }

        public void setRetentionHours(int retentionHours) {
            this.retentionHours = retentionHours;
        }

        public int getMaxMemoryEntries() {
            return maxMemoryEntries;
        }

        public void setMaxMemoryEntries(int maxMemoryEntries) {
            this.maxMemoryEntries = maxMemoryEntries;
        }
    }
}
