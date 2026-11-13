package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.Role;
import com.foundation.authz.domain.RoleScope;
import com.foundation.authz.infrastructure.mapper.RoleMapper;
import com.foundation.authz.infrastructure.mapper.RolePermissionMapper;
import com.foundation.authz.infrastructure.persistence.RoleDO;
import com.foundation.authz.infrastructure.persistence.RolePermissionDO;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 角色仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisRoleRepository implements RoleRepository {

    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;

    public MybatisRoleRepository(RoleMapper roleMapper, RolePermissionMapper rolePermissionMapper) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
    }

    @Override
    public List<Role> list() {
        Map<String, Set<String>> permissions = permissionsByRole();
        return roleMapper.selectList(Wrappers.<RoleDO>lambdaQuery().orderByAsc(RoleDO::getCode))
                .stream()
                .map(row -> toDomain(row, permissions.getOrDefault(row.getCode(), Set.of())))
                .toList();
    }

    @Override
    public Optional<Role> find(String code) {
        RoleDO row = roleMapper.selectById(code);
        if (row == null) {
            return Optional.empty();
        }
        Set<String> permissions = rolePermissionMapper.selectList(Wrappers.<RolePermissionDO>lambdaQuery()
                        .eq(RolePermissionDO::getRoleCode, code))
                .stream().map(RolePermissionDO::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        return Optional.of(toDomain(row, permissions));
    }

    @Override
    public void insert(Role role) {
        RoleDO row = new RoleDO();
        row.setCode(role.code());
        row.setName(role.name());
        row.setScope(role.scope().name());
        row.setAppId(role.appId());
        row.setBuiltIn(role.builtIn());
        row.setDataScope(role.dataScope());
        roleMapper.insert(row);
        replacePermissions(role.code(), role.permissions());
    }

    @Override
    public void update(Role role) {
        roleMapper.update(null, Wrappers.<RoleDO>lambdaUpdate()
                .set(RoleDO::getName, role.name())
                .set(RoleDO::getDataScope, role.dataScope())
                .set(RoleDO::getScope, role.scope().name())
                .set(RoleDO::getAppId, role.appId())
                .eq(RoleDO::getCode, role.code()));
        replacePermissions(role.code(), role.permissions());
    }

    @Override
    public void replacePermissions(String roleCode, Set<String> permissionCodes) {
        rolePermissionMapper.delete(Wrappers.<RolePermissionDO>lambdaQuery()
                .eq(RolePermissionDO::getRoleCode, roleCode));
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        for (String code : permissionCodes) {
            rolePermissionMapper.insert(new RolePermissionDO(roleCode, code));
        }
    }

    @Override
    public void delete(String code) {
        rolePermissionMapper.delete(Wrappers.<RolePermissionDO>lambdaQuery()
                .eq(RolePermissionDO::getRoleCode, code));
        // 主数据走逻辑删（48-CS §4）：MP 依 @TableLogic 转 UPDATE，不物理删除
        roleMapper.deleteById(code);
    }

    private Map<String, Set<String>> permissionsByRole() {
        return rolePermissionMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(RolePermissionDO::getRoleCode,
                        Collectors.mapping(RolePermissionDO::getCode,
                                Collectors.toCollection(LinkedHashSet::new))));
    }

    private static Role toDomain(RoleDO row, Set<String> permissions) {
        return new Role(row.getCode(), row.getName(), RoleScope.of(row.getScope()), row.getAppId(),
                Boolean.TRUE.equals(row.getBuiltIn()), row.getDataScope(), permissions);
    }
}
