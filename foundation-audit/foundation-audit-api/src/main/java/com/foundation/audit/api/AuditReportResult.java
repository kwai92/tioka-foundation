package com.foundation.audit.api;

/**
 * 审计上报结果（F-09 BAS-10 §上报）。
 *
 * @param accepted 本次接收条数
 */
public record AuditReportResult(int accepted) {
}
