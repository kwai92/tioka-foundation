package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.HighRiskReview;
import com.foundation.authz.domain.ReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 高风险授权复核仓储（F-09 BAS-19 §数据模型）。 */
public interface HighRiskReviewRepository {

    /** 追加一条复核。 */
    void insert(HighRiskReview review);

    /** 按主键查询。 */
    Optional<HighRiskReview> find(String id);

    /** 更新裁决结果（状态 / 复核人 / 复核时刻 / 意见）。 */
    void updateDecision(HighRiskReview review);

    /**
     * 状态游标分页（按申请时刻倒序）。
     *
     * @param status   状态过滤，{@code null} 表示不限
     * @param cursorTs 游标时刻，配合 {@code cursorId}
     * @param cursorId 游标 ID
     * @param size     每页条数
     */
    List<HighRiskReview> page(ReviewStatus status, Instant cursorTs, String cursorId, int size);
}
