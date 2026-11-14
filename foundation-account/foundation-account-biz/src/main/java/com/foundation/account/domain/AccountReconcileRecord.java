package com.foundation.account.domain;

import com.foundation.account.api.dto.AccountReconcileView;

import java.time.Instant;

/**
 * 每日对账记录（F-04 §6；{@code fdn_account_reconcile}）：只追加，无逻辑删与 {@code update_*} 列。
 *
 * @param id         记录 ID（{@code rec_…}）
 * @param ranAt      执行时间
 * @param total      参与对账的账号总数
 * @param drifted    发现差异数
 * @param fixed      已修复数
 * @param held       是否因超阈值暂停修复
 * @param detailJson 差异明细（JSON），可空
 */
public record AccountReconcileRecord(String id,
                                     Instant ranAt,
                                     int total,
                                     int drifted,
                                     int fixed,
                                     boolean held,
                                     String detailJson) {

    /** 转对外视图。 */
    public AccountReconcileView toView() {
        return new AccountReconcileView(id, ranAt, total, drifted, fixed, held, detailJson);
    }
}
