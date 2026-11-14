package com.foundation.authz.api;

/**
 * 高风险授权复核契约（F-09 BAS-19 §接口）。
 *
 * <p>权限码 {@code fdn:hrgrant:review}（HIGH，安全管理员）；授予的互斥校验并入
 * {@code POST /api/grants}（见 {@code RoleGrantService}），不复用本契约。
 */
public interface HighRiskReviewApi {

    /**
     * 复核列表（待办 / 历史）。
     *
     * @param status     状态过滤（{@code PENDING} / {@code APPROVED} / {@code REJECTED}），空表示不限
     * @param cursor     游标（{@code <createAtEpochMilli>|<id>}），空表示首页
     * @param size       每页条数（上限 100）
     */
    HighRiskReviewPageView page(String status, String cursor, int size);

    /** 通过：待生效授权落库生效。 */
    HighRiskReviewView approve(String reviewId, String reviewer, String reason);

    /** 拒绝：授权永不生效。 */
    HighRiskReviewView reject(String reviewId, String reviewer, String reason);
}
