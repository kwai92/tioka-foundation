package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.OrgAdminScope;

import java.util.List;
import java.util.Optional;

/** 组织分级管理员范围仓储（F-05 BAS-27 §分级授权）。 */
public interface OrgAdminScopeRepository {

    /**
     * 查询范围。
     *
     * @param userId 管理员用户 ID，{@code null} 表示不限（返回全部）
     */
    List<OrgAdminScope> list(String userId);

    /** 按主键查询。 */
    Optional<OrgAdminScope> findById(String id);

    /** 按「管理员 + 组织单元」查询。 */
    Optional<OrgAdminScope> findByUserAndOrgUnit(String userId, String orgUnitId);

    /** 新增范围。 */
    void insert(OrgAdminScope scope);

    /** 更新范围。 */
    void update(OrgAdminScope scope);
}
