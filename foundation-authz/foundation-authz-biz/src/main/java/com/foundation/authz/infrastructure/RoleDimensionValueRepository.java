package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.RoleDimensionValue;

import java.util.List;

/** 角色维度取值仓储（F-06 BAS-06 §数据模型 {@code fdn_role_dimension_value}）。 */
public interface RoleDimensionValueRepository {

    /** 角色的全部维度取值。 */
    List<RoleDimensionValue> listByRole(String roleCode);

    /** 清空角色的维度取值（覆盖式写入前）。 */
    void deleteByRole(String roleCode);

    /** 新增取值行。 */
    void insert(RoleDimensionValue value);
}
