package com.foundation.starter.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存接入配置（F-02 BAS-24 §foundation-starter-cache：{@code foundation.cache.*}）。
 *
 * <p>约定键前缀、锁与限流的实现选择，以及分布式锁的等待与租约时长。
 */
@ConfigurationProperties(prefix = "foundation.cache")
public class FoundationCacheProperties {

    /** 历史默认前缀；与 {@code DeploymentNamespace.DEFAULT_KEY_PREFIX} 一致，用于识别「未显式配置」。 */
    public static final String DEFAULT_KEY_PREFIX = "fdn:";

    /** 全局键前缀；缓存键、锁键与限流键统一以此开头，避免与同 Redis 实例的其他应用冲突。 */
    private String keyPrefix = DEFAULT_KEY_PREFIX;

    /** 锁与限流实现选择：{@code local}（单实例进程内）/ {@code redisson}（跨实例）。 */
    private String backend = "local";

    /** 获取分布式锁的最长等待时间；超时即失败（业务侧决定重试或直接拒绝）。 */
    private Duration lockWait = Duration.ofSeconds(3);

    /** 分布式锁租约；到期自动释放，避免持锁进程崩溃导致死锁。 */
    private Duration lockLease = Duration.ofSeconds(30);

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public Duration getLockWait() {
        return lockWait;
    }

    public void setLockWait(Duration lockWait) {
        this.lockWait = lockWait;
    }

    public Duration getLockLease() {
        return lockLease;
    }

    public void setLockLease(Duration lockLease) {
        this.lockLease = lockLease;
    }
}
