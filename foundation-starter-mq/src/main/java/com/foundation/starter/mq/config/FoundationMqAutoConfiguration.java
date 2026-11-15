package com.foundation.starter.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.starter.core.config.CoreAutoConfiguration;
import com.foundation.starter.mq.consumer.ConsumedMessageMapper;
import com.foundation.starter.mq.consumer.MybatisConsumedStore;
import com.foundation.starter.mq.mq.LoggingMqTemplate;
import com.foundation.starter.mq.mq.MqTemplate;
import com.foundation.starter.mq.mq.RocketMqMqTemplate;
import com.foundation.starter.mq.outbox.MybatisOutbox;
import com.foundation.starter.mq.outbox.MybatisPlusOutboxAppender;
import com.foundation.starter.mq.outbox.Outbox;
import com.foundation.starter.mq.outbox.OutboxMapper;
import com.foundation.starter.mq.relay.EventSink;
import com.foundation.starter.mq.relay.OutboxRelay;
import com.foundation.starter.mq.relay.WebhookEventSink;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 消息接入自动装配（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>总开关 {@code foundation.enabled}；MyBatis-Plus 相关 Bean 由嵌套配置在存在
 * {@code BaseMapper} 与 {@link SqlSessionFactory} 时提供；RocketMQ 相关 Bean 由嵌套配置在存在
 * {@link RocketMQTemplate} 时提供，缺失时退化为 {@link LoggingMqTemplate}。
 */
@AutoConfiguration(after = CoreAutoConfiguration.class)
@ConditionalOnFoundationCapability("mq")
@EnableConfigurationProperties(FoundationMqProperties.class)
public class FoundationMqAutoConfiguration {

    /** 默认投递实现（无 RocketMQ 时）。 */
    @Bean
    @ConditionalOnMissingBean(MqTemplate.class)
    public LoggingMqTemplate loggingMqTemplate(FoundationMqProperties properties) {
        return new LoggingMqTemplate(properties);
    }

    /** outbox 中继；仅 {@code foundation.mq.relay.enabled=true} 时创建并启动。 */
    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "foundation.mq.relay", name = "enabled", havingValue = "true")
    public OutboxRelay outboxRelay(Outbox outbox, MqTemplate mqTemplate,
                                   FoundationMqProperties properties, ObjectMapper objectMapper,
                                   ObjectProvider<EventSink> sinks) {
        return new OutboxRelay(outbox, mqTemplate, properties, objectMapper, sinks.orderedStream().toList());
    }

    /** 基于 MyBatis-Plus 的 outbox 与消费幂等（存在 BaseMapper 与 SqlSessionFactory 时）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.baomidou.mybatisplus.core.mapper.BaseMapper")
    @ConditionalOnBean(SqlSessionFactory.class)
    static class MybatisMqConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public MybatisOutbox mybatisOutbox(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
            return new MybatisOutbox(outboxMapper, objectMapper);
        }

        @Bean
        @ConditionalOnMissingBean
        public MybatisPlusOutboxAppender mybatisPlusOutboxAppender(Outbox outbox) {
            return new MybatisPlusOutboxAppender(outbox);
        }

        @Bean
        @ConditionalOnMissingBean
        public MybatisConsumedStore mybatisConsumedStore(ConsumedMessageMapper consumedMessageMapper) {
            return new MybatisConsumedStore(consumedMessageMapper);
        }
    }

    /** Webhook 并列投递通道（ADP-02）：显式开启才装配，缺 url/secret 时启动失败。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "foundation.mq.webhook", name = "enabled", havingValue = "true")
    static class WebhookConfiguration {

        @Bean
        @ConditionalOnMissingBean(EventSink.class)
        public WebhookEventSink webhookEventSink(FoundationMqProperties properties, ObjectMapper objectMapper) {
            return new WebhookEventSink(properties.getWebhook(), objectMapper);
        }
    }

    /** RocketMQ 投递实现（存在 {@link RocketMQTemplate} 时）。 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
    static class RocketMqConfiguration {

        @Bean
        @ConditionalOnMissingBean(MqTemplate.class)
        public RocketMqMqTemplate rocketMqMqTemplate(RocketMQTemplate rocketMQTemplate,
                                                     FoundationMqProperties properties) {
            return new RocketMqMqTemplate(rocketMQTemplate, properties);
        }
    }
}
