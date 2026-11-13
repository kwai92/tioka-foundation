package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.DimensionValue;
import com.foundation.authz.infrastructure.mapper.DimensionValueMapper;
import com.foundation.authz.infrastructure.persistence.DimensionValueDO;

import java.util.List;
import java.util.Optional;

/** 维度值仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisDimensionValueRepository implements DimensionValueRepository {

    private final DimensionValueMapper mapper;

    public MybatisDimensionValueRepository(DimensionValueMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DimensionValue> listByDimension(String dimensionId) {
        return mapper.selectList(Wrappers.<DimensionValueDO>lambdaQuery()
                        .eq(DimensionValueDO::getDimensionId, dimensionId)
                        .orderByAsc(DimensionValueDO::getCode))
                .stream().map(MybatisDimensionValueRepository::toDomain).toList();
    }

    @Override
    public Optional<DimensionValue> findByCode(String dimensionId, String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<DimensionValueDO>lambdaQuery()
                        .eq(DimensionValueDO::getDimensionId, dimensionId)
                        .eq(DimensionValueDO::getCode, code)))
                .map(MybatisDimensionValueRepository::toDomain);
    }

    @Override
    public void insert(DimensionValue value) {
        DimensionValueDO row = new DimensionValueDO();
        row.setId(value.id());
        row.setDimensionId(value.dimensionId());
        row.setCode(value.code());
        row.setName(value.name());
        row.setParentId(value.parentId());
        row.setActive(value.active());
        mapper.insert(row);
    }

    @Override
    public void update(DimensionValue value) {
        DimensionValueDO row = new DimensionValueDO();
        row.setId(value.id());
        row.setName(value.name());
        row.setParentId(value.parentId());
        row.setActive(value.active());
        mapper.updateById(row);
    }

    @Override
    public void deactivate(String dimensionId, String code) {
        mapper.update(null, Wrappers.<DimensionValueDO>lambdaUpdate()
                .set(DimensionValueDO::getActive, false)
                .eq(DimensionValueDO::getDimensionId, dimensionId)
                .eq(DimensionValueDO::getCode, code));
    }

    @Override
    public void deleteByDimension(String dimensionId) {
        // 逻辑删（@TableLogic），保留行便于回查
        mapper.delete(Wrappers.<DimensionValueDO>lambdaQuery()
                .eq(DimensionValueDO::getDimensionId, dimensionId));
    }

    private static DimensionValue toDomain(DimensionValueDO row) {
        return new DimensionValue(row.getId(), row.getDimensionId(), row.getCode(), row.getName(),
                row.getParentId(), Boolean.TRUE.equals(row.getActive()));
    }
}
