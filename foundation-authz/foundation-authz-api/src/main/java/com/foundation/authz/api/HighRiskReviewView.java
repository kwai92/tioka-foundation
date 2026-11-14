package com.foundation.authz.api;

import java.time.Instant;

/**
 * 高风险授权复核视图（F-09 BAS-19 §接口）。
 *
 * @param id            复核 ID（{@code hrr_…}）
 * @param grantId       待生效授权 ID（{@code grt_…}）
 * @param roleCode      角色编码
 * @param permissionCode 触发复核的高风险权限码
 * @param subjectType   授权对象类型
 * @param subjectId     授予对象 ID
 * @param requestedBy   申请人
 * @param status        {@code PENDING} / {@code APPROVED} / {@code REJECTED}
 * @param reviewerId    复核人（安全管理员），未复核时为空
 * @param reviewedAt    复核时刻，未复核时为空
 * @param reason        复核意见，可空
 * @param createAt      申请时刻
 */
public record HighRiskReviewView(String id,
                                 String grantId,
                                 String roleCode,
                                 String permissionCode,
                                 String subjectType,
                                 String subjectId,
                                 String requestedBy,
                                 String status,
                                 String reviewerId,
                                 Instant reviewedAt,
                                 String reason,
                                 Instant createAt) {
}
