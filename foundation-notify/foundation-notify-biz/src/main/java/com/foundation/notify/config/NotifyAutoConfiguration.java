package com.foundation.notify.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.authz.api.GrantApi;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.notify.api.AnnouncementApi;
import com.foundation.notify.api.AnnouncementController;
import com.foundation.notify.api.MessageApi;
import com.foundation.notify.api.MessageController;
import com.foundation.notify.api.MessageTemplateApi;
import com.foundation.notify.api.MessageTemplateController;
import com.foundation.notify.application.AnnouncementService;
import com.foundation.notify.application.FeishuNotificationChannel;
import com.foundation.notify.application.InboxChannel;
import com.foundation.notify.application.MailNotificationChannel;
import com.foundation.notify.application.MessageRetentionJob;
import com.foundation.notify.application.MessageService;
import com.foundation.notify.application.MessageTemplateService;
import com.foundation.notify.application.NotificationChannel;
import com.foundation.notify.application.UnreadCounter;
import com.foundation.notify.infrastructure.AnnouncementEventPublisher;
import com.foundation.notify.infrastructure.AnnouncementRepository;
import com.foundation.notify.infrastructure.MessageEventPublisher;
import com.foundation.notify.infrastructure.MessageReceiverRepository;
import com.foundation.notify.infrastructure.MessageRepository;
import com.foundation.notify.infrastructure.MessageTemplateRepository;
import com.foundation.notify.infrastructure.MybatisAnnouncementRepository;
import com.foundation.notify.infrastructure.MybatisMessageReceiverRepository;
import com.foundation.notify.infrastructure.MybatisMessageRepository;
import com.foundation.notify.infrastructure.MybatisMessageTemplateRepository;
import com.foundation.notify.infrastructure.mapper.AnnouncementMapper;
import com.foundation.notify.infrastructure.mapper.MessageMapper;
import com.foundation.notify.infrastructure.mapper.MessageReceiverMapper;
import com.foundation.notify.infrastructure.mapper.MessageTemplateMapper;
import com.foundation.org.api.PersonApi;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

/**
 * 消息中心自动装配（F-09 BAS-16）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 渠道 SPI → 应用服务 → {@code -api} 契约 Bean → Controller。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）、{@link OutboxAppender}/{@link EventProperties}（事件 outbox）、
 * {@link PersonApi}（公告按组织展开）与 {@link GrantApi}（公告按角色展开）；任一缺失时整块跳过。
 * Redis（{@link StringRedisTemplate}）可选，缺失时未读数降级为每次走 DB。
 *
 * <p>顺带开启 {@code @EnableScheduling}：保留期清理 {@link MessageRetentionJob} 依赖 {@code @Scheduled} 生效。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "notify", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration",
        "com.foundation.org.config.OrgAutoConfiguration",
        "com.foundation.authz.config.AuthzAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, OutboxAppender.class, EventProperties.class,
        PersonApi.class, GrantApi.class})
@EnableConfigurationProperties(NotifyProperties.class)
@EnableScheduling
public class NotifyAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public MessageRepository messageRepository(MessageMapper mapper, ObjectMapper objectMapper) {
        return new MybatisMessageRepository(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageReceiverRepository messageReceiverRepository(MessageReceiverMapper mapper,
                                                               MessageMapper messageMapper) {
        return new MybatisMessageReceiverRepository(mapper, messageMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplateRepository messageTemplateRepository(MessageTemplateMapper mapper) {
        return new MybatisMessageTemplateRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnouncementRepository announcementRepository(AnnouncementMapper mapper) {
        return new MybatisAnnouncementRepository(mapper);
    }

    // ---------- 事件发布 ----------

    @Bean
    @ConditionalOnMissingBean
    public MessageEventPublisher messageEventPublisher(OutboxAppender outbox, EventProperties eventProperties) {
        return new MessageEventPublisher(outbox, eventProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnouncementEventPublisher announcementEventPublisher(OutboxAppender outbox,
                                                                 EventProperties eventProperties) {
        return new AnnouncementEventPublisher(outbox, eventProperties);
    }

    // ---------- 渠道 SPI（ADR-026） ----------

    // 返回具体类型：@ConditionalOnMissingBean 按返回类型判存，避免三渠道同类型互相吞掉。

    @Bean
    @ConditionalOnMissingBean
    public InboxChannel inboxChannel() {
        return new InboxChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public MailNotificationChannel mailNotificationChannel() {
        return new MailNotificationChannel();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeishuNotificationChannel feishuNotificationChannel() {
        return new FeishuNotificationChannel();
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public UnreadCounter unreadCounter(MessageReceiverRepository receivers,
                                       ObjectProvider<StringRedisTemplate> redis) {
        return new UnreadCounter(receivers, redis.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageService messageService(MessageRepository messages, MessageReceiverRepository receivers,
                                         MessageTemplateRepository templates,
                                         List<NotificationChannel> channels, UnreadCounter unreadCounter,
                                         MessageEventPublisher messageEventPublisher,
                                         NotifyProperties properties) {
        return new MessageService(messages, receivers, templates, channels, unreadCounter,
                messageEventPublisher, properties);
    }

    @Bean
    @ConditionalOnMissingBean(MessageApi.class)
    public MessageApi messageApi(MessageService service) {
        return service;
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnouncementApi announcementApi(AnnouncementRepository announcements, MessageRepository messages,
                                           MessageReceiverRepository receivers, PersonApi persons,
                                           GrantApi grants, AnnouncementEventPublisher announcementEventPublisher,
                                           UnreadCounter unreadCounter) {
        return new AnnouncementService(announcements, messages, receivers, persons, grants,
                announcementEventPublisher, unreadCounter);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplateApi messageTemplateApi(MessageTemplateRepository templates) {
        return new MessageTemplateService(templates);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageRetentionJob messageRetentionJob(MessageService messageService, NotifyProperties properties) {
        return new MessageRetentionJob(messageService, properties);
    }

    // ---------- REST 控制器 ----------
    // 与上列 Bean 同生命周期注册：无持久化上下文时整块跳过，避免组件扫描出的控制器装配失败。

    @Bean
    @ConditionalOnMissingBean
    public MessageController messageController(MessageApi messages) {
        return new MessageController(messages);
    }

    @Bean
    @ConditionalOnMissingBean
    public AnnouncementController announcementController(AnnouncementApi announcements) {
        return new AnnouncementController(announcements);
    }

    @Bean
    @ConditionalOnMissingBean
    public MessageTemplateController messageTemplateController(MessageTemplateApi templates) {
        return new MessageTemplateController(templates);
    }
}
