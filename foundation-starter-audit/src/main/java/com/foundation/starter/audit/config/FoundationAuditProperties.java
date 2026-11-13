package com.foundation.starter.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 审计上报配置（F-07 §13：{@code foundation.audit.*}）。 */
@ConfigurationProperties(prefix = "foundation.audit")
public class FoundationAuditProperties {

    /** 上报批大小。 */
    private int batchSize = 100;

    /** 上报刷新间隔。 */
    private Duration flushInterval = Duration.ofSeconds(2);

    /** 本地有界队列容量；满时直接落盘（磁盘兜底）。 */
    private int queueCapacity = 1000;

    /** 本地磁盘兜底目录（文件，不落库）。 */
    private String spoolDir = System.getProperty("java.io.tmpdir") + "/fdn-audit-spool";

    /** 失败重试。 */
    private final Retry retry = new Retry();

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public Duration getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Duration flushInterval) {
        this.flushInterval = flushInterval;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public String getSpoolDir() {
        return spoolDir;
    }

    public void setSpoolDir(String spoolDir) {
        this.spoolDir = spoolDir;
    }

    public Retry getRetry() {
        return retry;
    }

    /** 重试配置：超过 {@code maxAttempts} 转磁盘 spool（F-07 §6）。 */
    public static class Retry {

        private int maxAttempts = 5;
        private Duration baseDelay = Duration.ofMillis(200);

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
}
