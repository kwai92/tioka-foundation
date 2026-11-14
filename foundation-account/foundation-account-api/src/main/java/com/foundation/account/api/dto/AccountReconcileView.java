package com.foundation.account.api.dto;

import java.time.Instant;

/**
 * 每日对账记录视图（F-04 §6）。
 *
 * @param id         记录 ID（{@code rec_…}）
 * @param ranAt      执行时间
 * @param total      参与对账的账号总数
 * @param drifted    发现差异数
 * @param fixed      已修复数
 * @param held       是否因超阈值暂停修复
 * @param detailJson 差异明细（JSON）
 */
public record AccountReconcileView(String id,
                                   Instant ranAt,
                                   int total,
                                   int drifted,
                                   int fixed,
                                   boolean held,
                                   String detailJson) {
}
