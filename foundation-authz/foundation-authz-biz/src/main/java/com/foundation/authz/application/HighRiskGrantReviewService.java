package com.foundation.authz.application;

import com.foundation.authz.api.HighRiskReviewApi;
import com.foundation.authz.api.HighRiskReviewPageView;
import com.foundation.authz.api.HighRiskReviewView;
import com.foundation.authz.config.SeparationProperties;
import com.foundation.authz.domain.AuthzIdPrefix;
import com.foundation.authz.domain.HighRiskReview;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.ReviewStatus;
import com.foundation.authz.domain.RiskLevel;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleGrant;
import com.foundation.authz.infrastructure.HighRiskReviewRepository;
import com.foundation.authz.infrastructure.PermissionCodeRepository;
import com.foundation.authz.infrastructure.RoleGrantRepository;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.common.id.Ids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 高风险授权复核（F-09 BAS-19 §规则）：角色含 {@code risk_level=HIGH} 权限码时，授权先落一条
 * {@code PENDING} 复核且**不写 {@code fdn_role_grant}**（因此通过前不生效）；安全管理员
 * {@link #approve} 后授权才落库生效，{@link #reject} 则永不生效。
 */
public class HighRiskGrantReviewService implements HighRiskReviewApi {

    private static final Logger LOG = LoggerFactory.getLogger(HighRiskGrantReviewService.class);

    private final HighRiskReviewRepository reviews;
    private final RoleGrantRepository grants;
    private final PermissionCodeRepository permissionCodes;
    private final SeparationProperties properties;
    private final AuthzAuditWriter audit;

    public HighRiskGrantReviewService(HighRiskReviewRepository reviews, RoleGrantRepository grants,
                                      PermissionCodeRepository permissionCodes,
                                      SeparationProperties properties, AuthzAuditWriter audit) {
        this.reviews = reviews;
        this.grants = grants;
        this.permissionCodes = permissionCodes;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * 授权含高风险权限码时登记待复核并返回 {@code true}（调用方据此跳过授权落库）。
     *
     * @param grant    待生效授权
     * @param role     目标角色（含权限码集合）
     * @param operator 申请人
     */
    public boolean deferIfHighRisk(RoleGrant grant, Role role, String operator) {
        if (!properties.isHighRiskReview()) {
            return false;
        }
        List<String> highRisk = highRiskCodes(role);
        if (highRisk.isEmpty()) {
            return false;
        }
        HighRiskReview review = new HighRiskReview(Ids.newId(AuthzIdPrefix.HIGH_RISK_REVIEW), grant.id(),
                highRisk.get(0), grant.roleCode(), grant.subjectType(), grant.subjectId(),
                grant.includeDescendants(), grant.effectiveFrom(), grant.effectiveTo(), grant.source(),
                operator, ReviewStatus.PENDING, null, null, null, Instant.now());
        reviews.insert(review);
        writeQuietly("authz.hrgrant.pending", "SUCCESS", operator, "grant:" + grant.id(),
                "role=" + grant.roleCode() + ";permission=" + highRisk.get(0));
        return true;
    }

    @Override
    public HighRiskReviewPageView page(String status, String cursor, int size) {
        int limit = size <= 0 ? 20 : Math.min(size, 100);
        ReviewStatus filter = status == null || status.isBlank() ? null : ReviewStatus.of(status);
        Instant cursorTs = null;
        String cursorId = null;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("\\|", 2);
            cursorTs = Instant.ofEpochMilli(Long.parseLong(parts[0]));
            cursorId = parts[1];
        }
        List<HighRiskReview> rows = reviews.page(filter, cursorTs, cursorId, limit);
        String nextCursor = null;
        if (rows.size() == limit && !rows.isEmpty()) {
            HighRiskReview last = rows.get(rows.size() - 1);
            nextCursor = last.createAt().toEpochMilli() + "|" + last.id();
        }
        return new HighRiskReviewPageView(rows.stream().map(HighRiskGrantReviewService::toView).toList(),
                nextCursor);
    }

    @Override
    @Transactional
    public HighRiskReviewView approve(String reviewId, String reviewer, String reason) {
        HighRiskReview review = require(reviewId);
        HighRiskReview decided = decide(review, ReviewStatus.APPROVED, reviewer, reason);
        grants.insert(review.toGrant());
        reviews.updateDecision(decided);
        writeQuietly("authz.hrgrant.approved", "SUCCESS", reviewer, "grant:" + review.grantId(),
                "role=" + review.roleCode() + ";review=" + review.id());
        return toView(decided);
    }

    @Override
    @Transactional
    public HighRiskReviewView reject(String reviewId, String reviewer, String reason) {
        HighRiskReview review = require(reviewId);
        HighRiskReview decided = decide(review, ReviewStatus.REJECTED, reviewer, reason);
        reviews.updateDecision(decided);
        writeQuietly("authz.hrgrant.rejected", "SUCCESS", reviewer, "grant:" + review.grantId(),
                "role=" + review.roleCode() + ";review=" + review.id());
        return toView(decided);
    }

    private HighRiskReview require(String reviewId) {
        HighRiskReview review = reviews.find(reviewId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "复核不存在：" + reviewId));
        if (review.status() != ReviewStatus.PENDING) {
            throw new BizException(ErrorCode.CONFLICT, "复核已完成：" + reviewId);
        }
        return review;
    }

    private static HighRiskReview decide(HighRiskReview review, ReviewStatus status, String reviewer,
                                         String reason) {
        return new HighRiskReview(review.id(), review.grantId(), review.permissionCode(), review.roleCode(),
                review.subjectType(), review.subjectId(), review.includeDescendants(), review.effectiveFrom(),
                review.effectiveTo(), review.source(), review.requestedBy(), status, reviewer, Instant.now(),
                reason, review.createAt());
    }

    /** 角色权限码中风险级别为 {@code HIGH} 的权限码。 */
    private List<String> highRiskCodes(Role role) {
        Set<String> codes = role.permissions();
        if (codes.isEmpty()) {
            return List.of();
        }
        return permissionCodes.listActive().stream()
                .filter(code -> code.riskLevel() == RiskLevel.HIGH)
                .map(PermissionCode::code)
                .filter(codes::contains)
                .toList();
    }

    private void writeQuietly(String action, String outcome, String operator, String resource, String detail) {
        try {
            audit.write(action, outcome, operator, resource, detail);
        } catch (RuntimeException ex) {
            LOG.warn("写入鉴权审计失败：{}（动作 {}）", ex.getMessage(), action);
        }
    }

    private static HighRiskReviewView toView(HighRiskReview review) {
        return new HighRiskReviewView(review.id(), review.grantId(), review.roleCode(),
                review.permissionCode(), review.subjectType().name(), review.subjectId(),
                review.requestedBy(), review.status().name(), review.reviewerId(), review.reviewedAt(),
                review.reason(), review.createAt());
    }
}
