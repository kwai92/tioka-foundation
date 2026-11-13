package com.foundation.starter.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 中台客户端配置（F-07 §13：{@code foundation.client.*} / {@code dict.*} / {@code param.*} / {@code org.*} / {@code person.*}）。 */
@ConfigurationProperties(prefix = "foundation")
public class FoundationClientProperties {

    /** 出站与重试（{@code foundation.client.*}）。 */
    private final Client client = new Client();

    /** 字典缓存（{@code foundation.dict.*}）。 */
    private final Cache dict = new Cache(Duration.ofMinutes(10));

    /** 参数缓存（{@code foundation.param.*}）。 */
    private final Cache param = new Cache(Duration.ofMinutes(10));

    /** 组织缓存（{@code foundation.org.*}）。 */
    private final Cache org = new Cache(Duration.ofMinutes(10));

    /** 人员缓存（{@code foundation.person.*}）。 */
    private final Cache person = new Cache(Duration.ofMinutes(10));

    /** 应用间调用（{@code foundation.app-grant.*}，占位，实现见 F-12）。 */
    private final AppGrant appGrant = new AppGrant();

    public Client getClient() {
        return client;
    }

    public Cache getDict() {
        return dict;
    }

    public Cache getParam() {
        return param;
    }

    public Cache getOrg() {
        return org;
    }

    public Cache getPerson() {
        return person;
    }

    public AppGrant getAppGrant() {
        return appGrant;
    }

    /** 出站与重试配置。 */
    public static class Client {

        private Duration connectTimeout = Duration.ofSeconds(1);
        private Duration readTimeout = Duration.ofSeconds(3);

        /** 读接口重试上限（指数退避 + 抖动）。 */
        private final Retry retry = new Retry();

        /** 熔断（短窗口失败率）。 */
        private final CircuitBreaker circuitBreaker = new CircuitBreaker();

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public Retry getRetry() {
            return retry;
        }

        public CircuitBreaker getCircuitBreaker() {
            return circuitBreaker;
        }
    }

    /** 重试配置。 */
    public static class Retry {

        private int maxAttempts = 3;
        private Duration baseDelay = Duration.ofMillis(100);

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBaseDelay() {
            return baseDelay;
        }

        public void setBaseDelay(Duration baseDelay) {
            this.baseDelay = baseDelay;
        }
    }

    /** 熔断配置。 */
    public static class CircuitBreaker {

        /** 失败率阈值（百分比），如 {@code 50}。 */
        private int failureRate = 50;

        /** 短窗口内最小样本数，低于该数不熔断。 */
        private int minCalls = 20;

        /** 统计窗口。 */
        private Duration window = Duration.ofSeconds(60);

        /** 熔断后开启时长，期满进入半开。 */
        private Duration openDuration = Duration.ofSeconds(30);

        public int getFailureRate() {
            return failureRate;
        }

        public void setFailureRate(int failureRate) {
            this.failureRate = failureRate;
        }

        public int getMinCalls() {
            return minCalls;
        }

        public void setMinCalls(int minCalls) {
            this.minCalls = minCalls;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }

        public Duration getOpenDuration() {
            return openDuration;
        }

        public void setOpenDuration(Duration openDuration) {
            this.openDuration = openDuration;
        }
    }

    /** 缓存配置（字典 / 参数 / 组织 / 人员同构：TTL 兜底 + 事件刷新）。 */
    public static class Cache {

        private Duration cacheTtl = Duration.ofMinutes(10);

        public Cache() {
        }

        public Cache(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    /** 应用间调用配置（占位）。 */
    public static class AppGrant {

        /** 授权关系缓存 TTL（事件丢失兜底）。 */
        private Duration cacheTtl = Duration.ofMinutes(5);

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }
}
