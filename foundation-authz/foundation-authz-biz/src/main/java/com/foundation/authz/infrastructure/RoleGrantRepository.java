package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.GrantSubjectType;
import com.foundation.authz.domain.RoleGrant;

import java.util.List;
import java.util.Optional;

/** 授权仓储（F-05 BAS-05 §领域模型）。 */
public interface RoleGrantRepository {

    /**
     * 查询授权。
     *
     * @param subjectType 授权对象类型，{@code null} 表示不限
     * @param subjectId   授予对象 ID，{@code null} 表示不限（配合类型取该类全部）
     */
    List<RoleGrant> list(GrantSubjectType subjectType, String subjectId);

    /** 按主键查询。 */
    Optional<RoleGrant> find(String id);

    /** 新增授权。 */
    void insert(RoleGrant grant);

    /** 撤销授权（逻辑删除）。 */
    void delete(String id);

    /** 角色的授权条数（删除角色前的引用校验）。 */
    long countByRole(String roleCode);
}
