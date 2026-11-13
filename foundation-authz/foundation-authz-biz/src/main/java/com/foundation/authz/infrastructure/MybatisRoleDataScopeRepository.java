package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.DeptScope;
import com.foundation.authz.domain.RoleDataScope;
import com.foundation.authz.infrastructure.mapper.RoleDataScopeMapper;
import com.foundation.authz.infrastructure.persistence.RoleDataScopeDO;

import java.util.Optional;

/** 角色数据范围仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisRoleDataScopeRepository implements RoleDataScopeRepository {

    private final RoleDataScopeMapper mapper;

    public MybatisRoleDataScopeRepository(RoleDataScopeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<RoleDataScope> findByRole(String roleCode) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<RoleDataScopeDO>lambdaQuery()
                        .eq(RoleDataScopeDO::getRoleCode, roleCode)))
                .map(MybatisRoleDataScopeRepository::toDomain);
    }

    @Override
    public void upsert(RoleDataScope scope) {
        RoleDataScopeDO existing = mapper.selectOne(Wrappers.<RoleDataScopeDO>lambdaQuery()
                .eq(RoleDataScopeDO::getRoleCode, scope.roleCode()));
        if (existing == null) {
            RoleDataScopeDO row = new RoleDataScopeDO();
            row.setId(scope.id());
            row.setRoleCode(scope.roleCode());
            row.setDeptScope(scope.deptScope().name());
            row.setDeptUnits(scope.deptUnits());
            mapper.insert(row);
            return;
        }
        RoleDataScopeDO row = new RoleDataScopeDO();
        row.setId(existing.getId());
        row.setDeptScope(scope.deptScope().name());
        row.setDeptUnits(scope.deptUnits());
        mapper.updateById(row);
    }

    private static RoleDataScope toDomain(RoleDataScopeDO row) {
        return new RoleDataScope(row.getId(), row.getRoleCode(), DeptScope.of(row.getDeptScope()),
                row.getDeptUnits());
    }
}
