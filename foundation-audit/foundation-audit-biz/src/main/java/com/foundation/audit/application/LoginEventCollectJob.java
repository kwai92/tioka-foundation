package com.foundation.audit.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 登录事件采集任务（F-09 BAS-10 §登录日志）。
 *
 * <p>默认每分钟一次从 Keycloak 拉取 LOGIN / LOGIN_ERROR 事件；异常只记 error，不向上抛出，
 * 避免调度线程因单次失败而终止。
 */
public class LoginEventCollectJob {

    private static final Logger log = LoggerFactory.getLogger(LoginEventCollectJob.class);

    private final LoginLogService loginLogService;

    public LoginEventCollectJob(LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
    }

    @Scheduled(cron = "${foundation.audit.login-collect.cron:0 * * * * *}")
    public void collect() {
        try {
            int added = loginLogService.collect();
            if (added > 0) {
                log.info("登录日志采集：新增 {} 条", added);
            }
        } catch (RuntimeException e) {
            log.error("登录日志采集失败", e);
        }
    }
}
