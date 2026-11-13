package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.time.LocalDate;
import java.util.List;

/**
 * 任职规则（F-03 §7.3）：一人一主任职、任职日期区间、主任职切换的原子性。
 *
 * <p>纯规则，不依赖 Spring 与持久化；「未结束主任职唯一」在库侧另有部分唯一索引
 * {@code uq_employment_primary} 兜底（F-03 §2.3）。
 */
public class EmploymentRule {

    /** 开始日期必填，结束日期不得早于开始日期。 */
    public void assertDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任职开始日期必填");
        }
        if (endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "任职结束日期早于开始日期");
        }
    }

    /**
     * 新增主任职前置校验：已存在其他未结束主任职时拒绝。
     *
     * @param excludeEmploymentId 排除的任职 ID（改任时传自身），可空
     * @param existing            该人员现有任职
     */
    public void assertPrimaryAvailable(String excludeEmploymentId, List<Employment> existing) {
        if (openPrimary(excludeEmploymentId, existing) != null) {
            throw new BizException(ErrorCode.CONFLICT, "该人员已存在未结束主任职，请先改任或结束原任职");
        }
    }

    /**
     * 主任职切换计划：把 {@code targetEmploymentId} 置为主任职前，需在同一事务内结束的既有主任职 ID。
     *
     * <p>返回非空即表示切换动作要「先结束旧的、再启用新的」，两步同事务，保证原子性。
     *
     * @param targetEmploymentId 目标任职 ID
     * @param existing           该人员现有任职
     * @return 需要结束的任职 ID（不含目标自身）
     */
    public List<String> primarySwitchPlan(String targetEmploymentId, List<Employment> existing) {
        return existing.stream()
                .filter(Employment::open)
                .filter(Employment::primary)
                .filter(employment -> !employment.id().equals(targetEmploymentId))
                .map(Employment::id)
                .toList();
    }

    private Employment openPrimary(String excludeEmploymentId, List<Employment> existing) {
        return existing.stream()
                .filter(Employment::open)
                .filter(Employment::primary)
                .filter(employment -> excludeEmploymentId == null
                        || !employment.id().equals(excludeEmploymentId))
                .findFirst()
                .orElse(null);
    }
}
