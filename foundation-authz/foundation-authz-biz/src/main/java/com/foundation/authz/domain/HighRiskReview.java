package com.foundation.authz.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 高风险授权复核（F-09 BAS-19 §数据模型）：授权含 {@code risk_level=HIGH} 权限码时先生成一条待复核，
 * 复核通过后才把 {@link #toGrant()} 落库生效，拒绝则永不生效。
 *
 * @param id                 复核 ID（{@code hrr_…}）
 * @param grantId            待生效授权 ID（{@code grt_…}）
 * @param permissionCode     触发复核的高风险权限码
 * @param roleCode           角色编码
 * @param subjectType        授权对象类型
 * @param subjectId          授予对象 ID
 * @param includeDescendants 是否含下级（仅 ORG_UNIT 有意义）
 * @param effectiveFrom      生效时间，可空
 * @param effectiveTo        失效时间，可空
 * @param source             授权来源
 * @param requestedBy        申请人
 * @param status             见 {@link ReviewStatus}
 * @param reviewerId         复核人，未复核时为空
 * @param reviewedAt         复核时刻，未复核时为空
 * @param reason             复核意见，可空
 * @param createAt           申请时刻
 */
public record HighRiskReview(String id,
                             String grantId,
                             String permissionCode,
                             String roleCode,
                             GrantSubjectType subjectType,
                             String subjectId,
                             boolean includeDescendants,
                             Instant effectiveFrom,
                             Instant effectiveTo,
                             GrantSource source,
                             String requestedBy,
                             ReviewStatus status,
                             String reviewerId,
                             Instant reviewedAt,
                             String reason,
                             Instant createAt) {

    public HighRiskReview {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(grantId, "grantId");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        status = status == null ? ReviewStatus.PENDING : status;
        source = source == null ? GrantSource.MANUAL : source;
    }

    /** 待生效授权负载：复核通过时据此落库。 */
    public RoleGrant toGrant() {
        return new RoleGrant(grantId, roleCode, subjectType, subjectId, includeDescendants,
                effectiveFrom, effectiveTo, source);
    }
}
