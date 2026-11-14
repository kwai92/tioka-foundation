package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.ToggleScopeType;

import java.util.List;
import java.util.Optional;

/** 功能开关仓储（F-05 BAS-27 §功能开关）。 */
public interface FeatureToggleRepository {

    /** 全部开关。 */
    List<FeatureToggle> list();

    /** 按作用域查询。 */
    Optional<FeatureToggle> find(ToggleScopeType scopeType, String scopeId);

    /** 按主键查询。 */
    Optional<FeatureToggle> findById(String id);

    /** 新增开关。 */
    void insert(FeatureToggle toggle);

    /** 更新开关（启用状态与原因）。 */
    void update(FeatureToggle toggle);
}
