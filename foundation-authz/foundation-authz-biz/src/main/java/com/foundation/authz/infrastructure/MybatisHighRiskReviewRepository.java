package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.HighRiskReview;
import com.foundation.authz.domain.ReviewStatus;
import com.foundation.authz.infrastructure.mapper.HighRiskReviewMapper;
import com.foundation.authz.infrastructure.persistence.HighRiskReviewDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 高风险授权复核仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisHighRiskReviewRepository implements HighRiskReviewRepository {

    private final HighRiskReviewMapper mapper;

    public MybatisHighRiskReviewRepository(HighRiskReviewMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(HighRiskReview review) {
        mapper.insert(toDO(review));
    }

    @Override
    public Optional<HighRiskReview> find(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisHighRiskReviewRepository::toDomain);
    }

    @Override
    public void updateDecision(HighRiskReview review) {
        HighRiskReviewDO row = new HighRiskReviewDO();
        row.setId(review.id());
        row.setStatus(review.status().name());
        row.setReviewerId(review.reviewerId());
        row.setReviewedAt(review.reviewedAt());
        row.setReason(review.reason());
        mapper.updateById(row);
    }

    @Override
    public List<HighRiskReview> page(ReviewStatus status, Instant cursorTs, String cursorId, int size) {
        String statusName = status == null ? null : status.name();
        LocalDateTime cursor = toUtc(cursorTs);
        return mapper.page(statusName, cursor, cursorId, size).stream()
                .map(MybatisHighRiskReviewRepository::toDomain).toList();
    }

    private static HighRiskReviewDO toDO(HighRiskReview review) {
        HighRiskReviewDO row = new HighRiskReviewDO();
        row.setId(review.id());
        row.setGrantId(review.grantId());
        row.setPermissionCode(review.permissionCode());
        row.setRoleCode(review.roleCode());
        row.setSubjectType(review.subjectType().name());
        row.setSubjectId(review.subjectId());
        row.setIncludeDescendants(review.includeDescendants());
        row.setEffectiveFrom(review.effectiveFrom());
        row.setEffectiveTo(review.effectiveTo());
        row.setSource(review.source().name());
        row.setRequestedBy(review.requestedBy());
        row.setStatus(review.status().name());
        row.setReviewerId(review.reviewerId());
        row.setReviewedAt(review.reviewedAt());
        row.setReason(review.reason());
        return row;
    }

    private static HighRiskReview toDomain(HighRiskReviewDO row) {
        return new HighRiskReview(row.getId(), row.getGrantId(), row.getPermissionCode(), row.getRoleCode(),
                GrantSubjectType.of(row.getSubjectType()), row.getSubjectId(),
                Boolean.TRUE.equals(row.getIncludeDescendants()), row.getEffectiveFrom(),
                row.getEffectiveTo(), GrantSource.of(row.getSource()), row.getRequestedBy(),
                ReviewStatus.of(row.getStatus()), row.getReviewerId(), row.getReviewedAt(),
                row.getReason(), toInstant(row.getCreateAt()));
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
