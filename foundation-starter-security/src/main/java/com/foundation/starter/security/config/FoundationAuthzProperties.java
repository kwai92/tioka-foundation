package com.foundation.starter.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 授权快照与事件配置（F-07 §13：{@code foundation.authz.*}）。 */
@ConfigurationProperties(prefix = "foundation.authz")
public class FoundationAuthzProperties {

    /** 快照缓存（F-07 §4）。 */
    private final Snapshot snapshot = new Snapshot();

    /** 事件订阅（F-07 §4）。 */
    private final Event event = new Event();

    public Snapshot getSnapshot() {
        return snapshot;
    }

    public Event getEvent() {
        return event;
    }

    /** 快照缓存配置。 */
    public static class Snapshot {

        /** 缓存 TTL（事件丢失兜底，S15 §11.3）。 */
        private Duration cacheTtl = Duration.ofMinutes(5);

        /** 缓存过期且取不到时的行为：{@code deny}（拒绝，不放行）。 */
        private String failMode = "deny";

        /** 进程内一级缓存（默认关）。 */
        private final LocalCache localCache = new LocalCache();

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public String getFailMode() {
            return failMode;
        }

        public void setFailMode(String failMode) {
            this.failMode = failMode;
        }

        public LocalCache getLocalCache() {
            return localCache;
        }

        /** 进程内一级缓存配置：TTL 极短（≤ 10 秒）且只缓存命中过的版本（F-07 §4）。 */
        public static class LocalCache {

            private boolean enabled = false;
            private Duration ttl = Duration.ofSeconds(10);

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public Duration getTtl() {
                return ttl;
            }

            public void setTtl(Duration ttl) {
                this.ttl = ttl;
            }
        }
    }

    /** 事件订阅配置。 */
    public static class Event {

        /** 事件主题（S15 §14.2）。 */
        private String topic = "fdn-events";

        /** 消费者组名；缺省为 {@code {appCode}-authz}。 */
        private String consumerGroup;

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getConsumerGroup() {
            return consumerGroup;
        }

        public void setConsumerGroup(String consumerGroup) {
            this.consumerGroup = consumerGroup;
        }
    }
}
