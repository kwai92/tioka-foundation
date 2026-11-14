package com.foundation.audit.application;

import com.foundation.audit.config.AuditProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;

/**
 * 审计保留期任务（F-09 BAS-10 §封印 / §归档）。
 *
 * <p>默认每日 03:30 执行：先对过去一天窗口做链封印（保证链连续），再归档超出在线保留期的分区。
 * 异常只记 error，不向上抛出，避免调度线程因单次失败而终止。
 */
public class AuditRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionJob.class);

    private final AuditSealer sealer;
    private final AuditArchiver archiver;
    private final AuditProperties properties;

    public AuditRetentionJob(AuditSealer sealer, AuditArchiver archiver, AuditProperties properties) {
        this.sealer = sealer;
        this.archiver = archiver;
        this.properties = properties;
    }

    @Scheduled(cron = "${foundation.audit.archive.cron:0 30 3 * * *}")
    public void run() {
        try {
            Instant to = Instant.now();
            Instant from = to.minus(Duration.ofDays(1));
            sealer.sealWindow(from, to);
            int archived = archiver.archiveExpired(to.minus(Duration.ofDays(properties.getOnlineDays())));
            if (archived > 0) {
                log.info("审计归档：删除 {} 个过期分区", archived);
            }
        } catch (RuntimeException e) {
            log.error("审计封印 / 归档任务失败", e);
        }
    }
}
