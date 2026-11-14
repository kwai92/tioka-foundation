package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.SubsystemModule;

import java.util.List;
import java.util.Optional;

/** 模块仓储（F-05 BAS-27 §目录）。 */
public interface ModuleRepository {

    /** 子系统下的模块（按排序升序）。 */
    List<SubsystemModule> listBySubsystem(String subsystemId);

    /** 按主键查询。 */
    Optional<SubsystemModule> findById(String id);

    /** 按子系统内编码查询。 */
    Optional<SubsystemModule> findByCode(String subsystemId, String code);

    /** 新增模块。 */
    void insert(SubsystemModule module);

    /** 更新模块（名称/排序/状态）。 */
    void update(SubsystemModule module);

    /** 标下线。 */
    void offline(String id);
}
