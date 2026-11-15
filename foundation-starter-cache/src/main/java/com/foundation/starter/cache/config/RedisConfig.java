package com.foundation.starter.cache.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 模板装配（F-02 BAS-24 §foundation-starter-cache）。
 *
 * <p>键用 {@link StringRedisSerializer}（可读、便于运维排查），值用
 * {@link GenericJackson2JsonRedisSerializer}；显式关闭事务支持，避免与业务事务耦合。
 *
 * <p>仅在类路径存在 Jackson 时装配（{@code ObjectMapper} 缺失则跳过，缓存能力降级为不启用）；
 * 应用未配置 Redis 时也不引入本模板。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
public class RedisConfig {

    /** 基础 Redis 模板（bean 名固定 {@code foundationRedisTemplate}，避免与框架默认模板冲突）。 */
    @Bean
    @ConditionalOnMissingBean(name = "foundationRedisTemplate")
    public RedisTemplate<String, Object> foundationRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setEnableTransactionSupport(false);
        return template;
    }
}
