package com.foundation.authz.api;

/**
 * 高风险授权复核裁决命令（F-09 BAS-19 §接口 {@code POST …/approve}、{@code …/reject}）。
 *
 * @param reason 复核意见（拒绝时建议填写）
 */
public record HighRiskReviewDecisionCmd(String reason) {
}
