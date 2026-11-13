package com.foundation.org.api.dto;

/**
 * 维护直属上级（F-03 §5 {@code PUT /api/fdn/persons/{id}/report-line}）。
 *
 * @param managerPersonId 直属上级人员 ID；空表示清除该人员的汇报关系
 */
public record ReportLineCmd(String managerPersonId) {
}
