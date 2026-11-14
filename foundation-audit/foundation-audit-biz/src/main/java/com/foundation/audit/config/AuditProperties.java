package com.foundation.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 审计与登录日志配置（F-09 BAS-10 §配置项 {@code foundation.audit}）。
 *
 * <p>注意：{@code foundation-starter-audit} 亦有同前缀的 {@code FoundationAuditProperties}（应用侧上报参数），
 * Spring Boot 允许多个 {@code @ConfigurationProperties} 共享前缀，此处字段与之不重叠。
 */
@ConfigurationProperties(prefix = "foundation.audit")
public class AuditProperties {

    /** 单批上报条数上限。 */
    private int reportMaxBatch = 200;

    /** 审计在线保留天数（小于 {@code 180} 时按下限 {@code 180} 保护）。 */
    private int onlineDays = 400;

    /** 登录事件采集 cron。 */
    private String loginCollectCron = "0 * * * * *";

    /** 审计封印 / 归档 cron。 */
    private String archiveCron = "0 30 3 * * *";

    public int getReportMaxBatch() {
        return reportMaxBatch;
    }

    public void setReportMaxBatch(int reportMaxBatch) {
        this.reportMaxBatch = reportMaxBatch;
    }

    public int getOnlineDays() {
        return onlineDays;
    }

    public void setOnlineDays(int onlineDays) {
        this.onlineDays = onlineDays < 180 ? 180 : onlineDays;
    }

    public String getLoginCollectCron() {
        return loginCollectCron;
    }

    public void setLoginCollectCron(String loginCollectCron) {
        this.loginCollectCron = loginCollectCron;
    }

    public String getArchiveCron() {
        return archiveCron;
    }

    public void setArchiveCron(String archiveCron) {
        this.archiveCron = archiveCron;
    }
}
