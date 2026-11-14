package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.SubsystemModule;
import com.foundation.authz.infrastructure.mapper.ModuleMapper;
import com.foundation.authz.infrastructure.persistence.ModuleDO;

import java.util.List;
import java.util.Optional;

/** 模块仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisModuleRepository implements ModuleRepository {

    private final ModuleMapper mapper;

    public MybatisModuleRepository(ModuleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SubsystemModule> listBySubsystem(String subsystemId) {
        return mapper.selectList(Wrappers.<ModuleDO>lambdaQuery()
                        .eq(ModuleDO::getSubsystemId, subsystemId)
                        .orderByAsc(ModuleDO::getSort)
                        .orderByAsc(ModuleDO::getCode))
                .stream().map(MybatisModuleRepository::toDomain).toList();
    }

    @Override
    public Optional<SubsystemModule> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisModuleRepository::toDomain);
    }

    @Override
    public Optional<SubsystemModule> findByCode(String subsystemId, String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ModuleDO>lambdaQuery()
                .eq(ModuleDO::getSubsystemId, subsystemId)
                .eq(ModuleDO::getCode, code))).map(MybatisModuleRepository::toDomain);
    }

    @Override
    public void insert(SubsystemModule module) {
        ModuleDO row = new ModuleDO();
        row.setId(module.id());
        row.setSubsystemId(module.subsystemId());
        row.setCode(module.code());
        row.setName(module.name());
        row.setSort(module.sort());
        row.setStatus(module.status());
        mapper.insert(row);
    }

    @Override
    public void update(SubsystemModule module) {
        ModuleDO row = new ModuleDO();
        row.setId(module.id());
        row.setName(module.name());
        row.setSort(module.sort());
        row.setStatus(module.status());
        mapper.updateById(row);
    }

    @Override
    public void offline(String id) {
        mapper.update(null, Wrappers.<ModuleDO>lambdaUpdate()
                .set(ModuleDO::getStatus, LifecycleStatus.OFFLINE)
                .eq(ModuleDO::getId, id));
    }

    private static SubsystemModule toDomain(ModuleDO row) {
        return new SubsystemModule(row.getId(), row.getSubsystemId(), row.getCode(), row.getName(),
                row.getSort() == null ? 0 : row.getSort(), row.getStatus());
    }
}
