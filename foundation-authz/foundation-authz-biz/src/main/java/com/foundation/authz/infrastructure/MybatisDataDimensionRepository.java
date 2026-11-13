package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.DataDimension;
import com.foundation.authz.domain.ValueSource;
import com.foundation.authz.infrastructure.mapper.DataDimensionMapper;
import com.foundation.authz.infrastructure.persistence.DataDimensionDO;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 数据维度仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisDataDimensionRepository implements DataDimensionRepository {

    private final DataDimensionMapper mapper;

    public MybatisDataDimensionRepository(DataDimensionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<DataDimension> list(String appCode) {
        var query = Wrappers.<DataDimensionDO>lambdaQuery();
        if (appCode == null || appCode.isBlank()) {
            query.isNull(DataDimensionDO::getAppCode);
        } else {
            query.and(condition -> condition.isNull(DataDimensionDO::getAppCode)
                    .or().eq(DataDimensionDO::getAppCode, appCode));
        }
        query.orderByAsc(DataDimensionDO::getCode);
        return mapper.selectList(query).stream().map(MybatisDataDimensionRepository::toDomain).toList();
    }

    @Override
    public List<DataDimension> listAll() {
        return mapper.selectList(Wrappers.<DataDimensionDO>lambdaQuery()
                        .orderByAsc(DataDimensionDO::getCode))
                .stream().map(MybatisDataDimensionRepository::toDomain).toList();
    }

    @Override
    public Optional<DataDimension> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisDataDimensionRepository::toDomain);
    }

    @Override
    public Optional<DataDimension> findByCode(String appCode, String code) {
        var query = Wrappers.<DataDimensionDO>lambdaQuery();
        if (appCode == null || appCode.isBlank()) {
            query.isNull(DataDimensionDO::getAppCode);
        } else {
            query.eq(DataDimensionDO::getAppCode, appCode);
        }
        query.eq(DataDimensionDO::getCode, code);
        return Optional.ofNullable(mapper.selectOne(query)).map(MybatisDataDimensionRepository::toDomain);
    }

    @Override
    public Optional<DataDimension> findByCodeAnywhere(String code) {
        return mapper.selectList(Wrappers.<DataDimensionDO>lambdaQuery()
                        .eq(DataDimensionDO::getCode, code))
                .stream().map(MybatisDataDimensionRepository::toDomain)
                .min(Comparator.comparingInt(dimension -> dimension.global() ? 0 : 1));
    }

    @Override
    public void insert(DataDimension dimension) {
        DataDimensionDO row = new DataDimensionDO();
        row.setId(dimension.id());
        row.setAppCode(dimension.appCode());
        row.setCode(dimension.code());
        row.setName(dimension.name());
        row.setValueSource(dimension.valueSource().name());
        row.setHierarchical(dimension.hierarchical());
        mapper.insert(row);
    }

    @Override
    public void update(DataDimension dimension) {
        DataDimensionDO row = new DataDimensionDO();
        row.setId(dimension.id());
        row.setName(dimension.name());
        row.setValueSource(dimension.valueSource().name());
        row.setHierarchical(dimension.hierarchical());
        mapper.updateById(row);
    }

    private static DataDimension toDomain(DataDimensionDO row) {
        return new DataDimension(row.getId(), row.getAppCode(), row.getCode(), row.getName(),
                ValueSource.of(row.getValueSource()), Boolean.TRUE.equals(row.getHierarchical()));
    }
}
