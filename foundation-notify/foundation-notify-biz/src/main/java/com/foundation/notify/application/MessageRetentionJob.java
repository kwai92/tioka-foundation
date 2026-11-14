package com.foundation.notify.application;

import com.foundation.notify.config.NotifyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 站内信保留期清理任务（F-09 BAS-16 §保留期）。
 *
 * <p>每日 03:30 清理超过保留期的已读接收行；由 {@code NotifyAutoConfiguration} 以 {@code @Bean} 装配，
 * 依赖其上 {@code @EnableScheduling} 使 {@link Scheduled} 生效。
 */
public class MessageRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(MessageRetentionJob.class);

    private final MessageService messageService;
    private final NotifyProperties properties;

    public MessageRetentionJob(MessageService messageService, NotifyProperties properties) {
        this.messageService = messageService;
        this.properties = properties;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredInbox() {
        int days = properties.getInbox().getRetentionDays();
        if (days <= 0) {
            return;
        }
        int removed = messageService.purgeReadBefore(days);
        if (removed > 0) {
            log.info("站内信保留期清理：删除 {} 条已读接收记录（保留 {} 天）", removed, days);
        }
    }
}
