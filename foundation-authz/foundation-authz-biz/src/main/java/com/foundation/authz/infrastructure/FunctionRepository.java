package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.SubsystemFunction;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 功能仓储（F-05 BAS-27 §目录）。 */
public interface FunctionRepository {

    /** 模块下的功能（按排序升序）。 */
    List<SubsystemFunction> listByModule(String moduleId);

    /** 多个模块下的功能。 */
    List<SubsystemFunction> listByModules(Collection<String> moduleIds);

    /** 按主键查询。 */
    Optional<SubsystemFunction> findById(String id);

    /** 按模块内编码查询。 */
    Optional<SubsystemFunction> findByCode(String moduleId, String code);

    /** 新增功能。 */
    void insert(SubsystemFunction function);

    /** 更新功能（名称/菜单/排序/状态）。 */
    void update(SubsystemFunction function);

    /** 标下线。 */
    void offline(String id);
}
