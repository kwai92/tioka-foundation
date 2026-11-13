package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.domain.Position;
import com.foundation.org.infrastructure.mapper.PositionMapper;
import com.foundation.org.infrastructure.persistence.PositionDO;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 岗位仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisPositionRepository implements PositionRepository {

    private final PositionMapper mapper;

    public MybatisPositionRepository(PositionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Position> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisPositionRepository::toDomain);
    }

    @Override
    public Optional<Position> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<PositionDO>lambdaQuery().eq(PositionDO::getCode, code))).map(MybatisPositionRepository::toDomain);
    }

    @Override
    public Optional<Position> findBySourceExternalId(String source, String externalId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<PositionDO>lambdaQuery()
                .eq(PositionDO::getSource, source)
                .eq(PositionDO::getExternalId, externalId))).map(MybatisPositionRepository::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return mapper.selectCount(Wrappers.<PositionDO>lambdaQuery().eq(PositionDO::getCode, code)) > 0;
    }

    @Override
    public List<Position> list(int page, int size) {
        Page<PositionDO> result = mapper.selectPage(new Page<>(page, size),
                Wrappers.<PositionDO>lambdaQuery().orderByAsc(PositionDO::getCode));
        return result.getRecords().stream().map(MybatisPositionRepository::toDomain).toList();
    }

    @Override
    public long count() {
        return mapper.selectCount(null);
    }

    @Override
    public List<Position> findByOrgUnit(String orgUnitId) {
        return mapper.selectList(Wrappers.<PositionDO>lambdaQuery()
                        .eq(PositionDO::getOrgUnitId, orgUnitId)
                        .orderByAsc(PositionDO::getSortNo))
                .stream().map(MybatisPositionRepository::toDomain).toList();
    }

    @Override
    public Map<String, Long> countByOrgUnit(Collection<String> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        mapper.selectList(Wrappers.<PositionDO>lambdaQuery().in(PositionDO::getOrgUnitId, orgUnitIds))
                .forEach(row -> counts.merge(row.getOrgUnitId(), 1L, Long::sum));
        return counts;
    }

    @Override
    public void insert(Position position) {
        mapper.insert(toDO(position));
    }

    @Override
    public void update(Position position) {
        mapper.update(null, Wrappers.<PositionDO>lambdaUpdate()
                .set(PositionDO::getCode, position.code())
                .set(PositionDO::getName, position.name())
                .set(PositionDO::getOrgUnitId, position.orgUnitId())
                .set(PositionDO::getStatus, position.status().name())
                .set(PositionDO::getSortNo, position.sortNo())
                .set(PositionDO::getSource, position.source())
                .set(PositionDO::getExternalId, position.externalId())
                .eq(PositionDO::getId, position.id()));
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    private static PositionDO toDO(Position position) {
        PositionDO row = new PositionDO();
        row.setId(position.id());
        row.setCode(position.code());
        row.setName(position.name());
        row.setOrgUnitId(position.orgUnitId());
        row.setStatus(position.status().name());
        row.setSortNo(position.sortNo());
        row.setSource(position.source());
        row.setExternalId(position.externalId());
        return row;
    }

    private static Position toDomain(PositionDO row) {
        return new Position(row.getId(), row.getCode(), row.getName(), row.getOrgUnitId(),
                OrgStatus.valueOf(row.getStatus()), row.getSortNo() == null ? 0 : row.getSortNo(),
                row.getSource(), row.getExternalId());
    }
}
