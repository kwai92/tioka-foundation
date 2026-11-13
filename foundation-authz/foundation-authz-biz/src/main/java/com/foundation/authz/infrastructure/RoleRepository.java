package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.Role;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 角色仓储（F-05 BAS-05 §领域模型）。 */
public interface RoleRepository {

    /** 全部角色（含权限码集合；逻辑删的角色不返回）。 */
    List<Role> list();

    /** 按角色编码查询。 */
    Optional<Role> find(String code);

    /** 新建角色（含权限码集合）。 */
    void insert(Role role);

    /** 更新名称与数据范围。 */
    void update(Role role);

    /** 整体重写角色的权限码集合。 */
    void replacePermissions(String roleCode, Set<String> permissionCodes);

    /** 逻辑删除角色（内置角色由调用方拦截）。 */
    void delete(String code);
}
