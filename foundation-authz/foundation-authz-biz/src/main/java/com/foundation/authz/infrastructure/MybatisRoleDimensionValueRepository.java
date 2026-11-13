package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.RoleDimensionValue;
import com.foundation.authz.infrastructure.mapper.RoleDimensionValueMapper;
import com.foundation.authz.infrastructure.persistence.RoleDimensionValueDO;

import java.util.List;

/** 角色维度取值仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisRoleDimensionValueRepository implements RoleDimensionValueRepository {

    private final RoleDimensionValueMapper mapper;

    public MybatisRoleDimensionValueRepository(RoleDimensionValueMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RoleDimensionValue> listByRole(String roleCode) {
        return mapper.selectList(Wrappers.<RoleDimensionValueDO>lambdaQuery()
                        .eq(RoleDimensionValueDO::getRoleCode, roleCode)
                        .orderByAsc(RoleDimensionValueDO::getId))
                .stream().map(MybatisRoleDimensionValueRepository::toDomain).toList();
    }

    @Override
    public void deleteByRole(String roleCode) {
        // 逻辑删（@TableLogic）
        mapper.delete(Wrappers.<RoleDimensionValueDO>lambdaQuery()
                .eq(RoleDimensionValueDO::getRoleCode, roleCode));
    }

    @Override
    public void insert(RoleDimensionValue value) {
        RoleDimensionValueDO row = new RoleDimensionValueDO();
        row.setId(value.id());
        row.setRoleCode(value.roleCode());
        row.setDimensionId(value.dimensionId());
        row.setValueCode(value.valueCode());
        row.setAllValues(value.allValues());
        mapper.insert(row);
    }

    private static RoleDimensionValue toDomain(RoleDimensionValueDO row) {
        return new RoleDimensionValue(row.getId(), row.getRoleCode(), row.getDimensionId(),
                row.getValueCode(), Boolean.TRUE.equals(row.getAllValues()));
    }
}
