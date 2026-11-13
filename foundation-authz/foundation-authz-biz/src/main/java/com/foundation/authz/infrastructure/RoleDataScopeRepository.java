package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.RoleDataScope;

import java.util.Optional;

/** 角色数据范围仓储（F-06 BAS-06 §数据模型 {@code fdn_role_data_scope}）；一角色一行。 */
public interface RoleDataScopeRepository {

    /** 按角色查询。 */
    Optional<RoleDataScope> findByRole(String roleCode);

    /** 覆盖式写入（存在则更新，否则新增）。 */
    void upsert(RoleDataScope scope);
}
