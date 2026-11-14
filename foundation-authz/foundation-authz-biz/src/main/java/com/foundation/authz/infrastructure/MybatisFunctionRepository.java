package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.SubsystemFunction;
import com.foundation.authz.infrastructure.mapper.FunctionMapper;
import com.foundation.authz.infrastructure.persistence.FunctionDO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 功能仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisFunctionRepository implements FunctionRepository {

    private final FunctionMapper mapper;

    public MybatisFunctionRepository(FunctionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SubsystemFunction> listByModule(String moduleId) {
        return mapper.selectList(Wrappers.<FunctionDO>lambdaQuery()
                        .eq(FunctionDO::getModuleId, moduleId)
                        .orderByAsc(FunctionDO::getSort)
                        .orderByAsc(FunctionDO::getCode))
                .stream().map(MybatisFunctionRepository::toDomain).toList();
    }

    @Override
    public List<SubsystemFunction> listByModules(Collection<String> moduleIds) {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.<FunctionDO>lambdaQuery()
                        .in(FunctionDO::getModuleId, moduleIds)
                        .orderByAsc(FunctionDO::getSort)
                        .orderByAsc(FunctionDO::getCode))
                .stream().map(MybatisFunctionRepository::toDomain).toList();
    }

    @Override
    public Optional<SubsystemFunction> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisFunctionRepository::toDomain);
    }

    @Override
    public Optional<SubsystemFunction> findByCode(String moduleId, String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<FunctionDO>lambdaQuery()
                .eq(FunctionDO::getModuleId, moduleId)
                .eq(FunctionDO::getCode, code))).map(MybatisFunctionRepository::toDomain);
    }

    @Override
    public void insert(SubsystemFunction function) {
        FunctionDO row = new FunctionDO();
        row.setId(function.id());
        row.setModuleId(function.moduleId());
        row.setCode(function.code());
        row.setName(function.name());
        row.setMenuId(function.menuId());
        row.setSort(function.sort());
        row.setStatus(function.status());
        mapper.insert(row);
    }

    @Override
    public void update(SubsystemFunction function) {
        FunctionDO row = new FunctionDO();
        row.setId(function.id());
        row.setName(function.name());
        row.setMenuId(function.menuId());
        row.setSort(function.sort());
        row.setStatus(function.status());
        mapper.updateById(row);
    }

    @Override
    public void offline(String id) {
        mapper.update(null, Wrappers.<FunctionDO>lambdaUpdate()
                .set(FunctionDO::getStatus, LifecycleStatus.OFFLINE)
                .eq(FunctionDO::getId, id));
    }

    private static SubsystemFunction toDomain(FunctionDO row) {
        return new SubsystemFunction(row.getId(), row.getModuleId(), row.getCode(), row.getName(),
                row.getMenuId(), row.getSort() == null ? 0 : row.getSort(), row.getStatus());
    }
}
