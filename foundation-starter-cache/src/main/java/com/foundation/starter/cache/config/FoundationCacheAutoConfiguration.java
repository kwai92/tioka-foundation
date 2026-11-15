package com.foundation.starter.cache.config;

import com.foundation.starter.cache.key.KeyPrefix;
import com.foundation.starter.cache.lock.DistributedLock;
import com.foundation.starter.cache.lock.LocalDistributedLock;
import com.foundation.starter.cache.lock.RedissonDistributedLock;
import com.foundation.starter.cache.ratelimit.LocalRateLimiter;
import com.foundation.starter.cache.ratelimit.RateLimiter;
import com.foundation.starter.cache.ratelimit.RedissonRateLimiter;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存接入自动装配（F-02 BAS-24 §foundation-starter-cache）。
 *
 * <p>缺省 {@code backend=local}：锁与限流退化为单实例进程内实现，无需 Redis；
 * 设为 {@code backend=redisson} 且类路径存在 Redisson 时，改用跨实例实现（按
 * {@code spring.data.redis.*} 自行装配 {@link RedissonClient}，避免与 Lettuce 抢装配）。
 *
 * <p>Redisson 相关类型只出现在被 {@link ConditionalOnClass} 守住的嵌套配置里，避免类加载失败。
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "foundation", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FoundationCacheProperties.class)
public class FoundationCacheAutoConfiguration {

    /** 键前缀（所有缓存键、锁键与限流键的统一前缀）。 */
    @Bean
    @ConditionalOnMissingBean
    public KeyPrefix foundationKeyPrefix(FoundationCacheProperties properties) {
        return new KeyPrefix(properties.getKeyPrefix());
    }

    /** 单实例分布式锁（{@code backend=local}，缺省）。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "foundation.cache", name = "backend", havingValue = "local",
            matchIfMissing = true)
    public DistributedLock foundationDistributedLock() {
        return new LocalDistributedLock();
    }

    /** 单实例限流器（{@code backend=local}，缺省）。 */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "foundation.cache", name = "backend", havingValue = "local",
            matchIfMissing = true)
    public RateLimiter foundationRateLimiter() {
        return new LocalRateLimiter();
    }

    /** Redisson 装配分支：仅 {@code backend=redisson} 且有 Redisson 类时生效。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.redisson.api.RedissonClient")
    @ConditionalOnProperty(prefix = "foundation.cache", name = "backend", havingValue = "redisson")
    static class RedissonCacheConfiguration {

        /** 按 {@code spring.data.redis.*} 组装单机 Redisson 客户端（与 Lettuce 并存但不互斥）。 */
        @Bean
        @ConditionalOnMissingBean
        public RedissonClient foundationRedissonClient(RedisProperties redisProperties) {
            Config config = new Config();
            SingleServerConfig server = config.useSingleServer()
                    .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort());
            String password = redisProperties.getPassword();
            if (password != null && !password.isBlank()) {
                server.setPassword(password);
            }
            server.setDatabase(redisProperties.getDatabase());
            return Redisson.create(config);
        }

        /** 跨实例分布式锁。 */
        @Bean
        @ConditionalOnMissingBean
        public RedissonDistributedLock foundationRedissonDistributedLock(RedissonClient redissonClient,
                                                                        KeyPrefix keyPrefix) {
            return new RedissonDistributedLock(redissonClient, keyPrefix);
        }

        /** 跨实例限流器。 */
        @Bean
        @ConditionalOnMissingBean
        public RedissonRateLimiter foundationRedissonRateLimiter(RedissonClient redissonClient,
                                                                 KeyPrefix keyPrefix) {
            return new RedissonRateLimiter(redissonClient, keyPrefix);
        }
    }
}
