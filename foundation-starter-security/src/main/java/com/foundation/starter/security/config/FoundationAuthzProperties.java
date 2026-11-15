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

        /**
         * 缓存过期且取不到时的行为：{@code deny}（拒绝，不放行，默认）或
         * {@code stale}（在 {@code stale-max-age} 内返回过期快照，需审计与降级标记）。
         * 身份与授权类建议保持 deny，见 36 号规划 §6 与待决策 D4。
         */
        private String failMode = "deny";

        /** {@code fail-mode=stale} 时可容忍的最大陈旧时长（默认 10 分钟）。 */
        private Duration staleMaxAge = Duration.ofMinutes(10);

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

        /** 解析 fail-mode；未知配置快速失败，避免把拼错的配置当 deny 悄悄放过。 */
        public boolean isStaleAllowed() {
            if ("stale".equalsIgnoreCase(failMode)) {
                return true;
            }
            if ("deny".equalsIgnoreCase(failMode)) {
                return false;
            }
            throw new IllegalArgumentException("非法的 foundation.authz.snapshot.fail-mode="
                    + failMode + "，可选 deny / stale");
        }

        public Duration getStaleMaxAge() {
            return staleMaxAge;
        }

        public void setStaleMaxAge(Duration staleMaxAge) {
            this.staleMaxAge = staleMaxAge == null ? Duration.ofMinutes(10) : staleMaxAge;
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
