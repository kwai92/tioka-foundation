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

    /** Webhook 事件通道配置（ADP-02）。 */
    private final Webhook webhook = new Webhook();

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

    public Webhook getWebhook() {
        return webhook;
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

    /** Webhook 并列投递通道（foundation.mq.webhook.*）。 */
    public static class Webhook {

        /** 总开关；缺省关闭。启用时 url 与 secret 必填，缺失则启动失败。 */
        private boolean enabled = false;

        /** 订阅端地址（HTTPS，生产强制）。 */
        private String url;

        /** HMAC-SHA256 签名密钥，经环境变量注入，不入库。 */
        private String secret;

        /** 连接超时。 */
        private Duration connectTimeout = Duration.ofSeconds(3);

        /** 单次请求超时。 */
        private Duration timeout = Duration.ofSeconds(5);

        /** 事件白名单；为空表示全部事件。 */
        private java.util.List<String> allowedEventTypes = java.util.List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public java.util.List<String> getAllowedEventTypes() {
            return allowedEventTypes;
        }

        public void setAllowedEventTypes(java.util.List<String> allowedEventTypes) {
            this.allowedEventTypes = allowedEventTypes;
        }
    }
}
