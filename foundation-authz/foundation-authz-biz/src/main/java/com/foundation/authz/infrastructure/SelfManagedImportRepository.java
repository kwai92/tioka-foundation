package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.SelfManagedImport;
import com.foundation.authz.domain.SelfManagedImportItem;

import java.util.List;
import java.util.Optional;

/** 自管一次性导入仓储（F-05 BAS-27 §自管导入）。 */
public interface SelfManagedImportRepository {

    /** 按主键查询批次。 */
    Optional<SelfManagedImport> findById(String id);

    /** 子系统下的批次。 */
    List<SelfManagedImport> listBySubsystem(String subsystemId);

    /** 新增批次。 */
    void insert(SelfManagedImport batch);

    /** 更新批次（状态与摘要）。 */
    void update(SelfManagedImport batch);

    /** 新增明细项。 */
    void insertItem(SelfManagedImportItem item);

    /** 批次的明细项。 */
    List<SelfManagedImportItem> items(String batchId);
}
