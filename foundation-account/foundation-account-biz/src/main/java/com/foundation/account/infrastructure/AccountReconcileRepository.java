package com.foundation.account.infrastructure;

import com.foundation.account.domain.AccountReconcileRecord;

import java.util.List;

/** 每日对账记录仓储（F-04 §6，MyBatis-Plus）：只追加。 */
public interface AccountReconcileRepository {

    void insert(AccountReconcileRecord record);

    /** 最近的若干次对账记录，按执行时间倒序。 */
    List<AccountReconcileRecord> recent(int limit);
}
