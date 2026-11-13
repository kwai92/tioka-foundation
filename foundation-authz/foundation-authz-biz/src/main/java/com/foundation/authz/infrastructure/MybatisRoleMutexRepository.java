package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.RoleMutex;
import com.foundation.authz.infrastructure.mapper.RoleMutexMapper;
import com.foundation.authz.infrastructure.persistence.RoleMutexDO;

import java.util.List;

/** 互斥角色仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisRoleMutexRepository implements RoleMutexRepository {

    private final RoleMutexMapper mapper;

    public MybatisRoleMutexRepository(RoleMutexMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RoleMutex> all() {
        return mapper.selectList(null).stream()
                .map(row -> new RoleMutex(row.getRoleCode(), row.getMutexRoleCode()))
                .toList();
    }
}
