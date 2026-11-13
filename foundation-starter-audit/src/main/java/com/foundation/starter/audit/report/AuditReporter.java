package com.foundation.starter.audit.report;

/**
 * 审计上报入口（F-07 §6）。
 *
 * <p>契约：**非阻塞**，事件入本地队列；投递、重试与磁盘兜底由实现负责。
 */
public interface AuditReporter {

    /** 上报一条审计；非阻塞，入本地队列。 */
    void report(AuditEvent event);
}
