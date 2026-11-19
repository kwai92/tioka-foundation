package com.foundation.org.infrastructure;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.org.domain.Employment;
import com.foundation.org.infrastructure.mapper.EmploymentMapper;
import com.foundation.org.infrastructure.persistence.EmploymentDO;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 任职仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisEmploymentRepository implements EmploymentRepository {

    private final EmploymentMapper mapper;

    public MybatisEmploymentRepository(EmploymentMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Employment> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisEmploymentRepository::toDomain);
    }

    @Override
    public List<Employment> byPerson(String personId) {
        return mapper.selectList(Wrappers.<EmploymentDO>lambdaQuery()
                        .eq(EmploymentDO::getPersonId, personId)
                        .orderByAsc(EmploymentDO::getStartDate))
                .stream().map(MybatisEmploymentRepository::toDomain).toList();
    }

    @Override
    public List<Employment> openByPerson(String personId) {
        return mapper.selectList(openWrapper().eq(EmploymentDO::getPersonId, personId))
                .stream().map(MybatisEmploymentRepository::toDomain).toList();
    }

    @Override
    public Optional<Employment> openPrimary(String personId) {
        return Optional.ofNullable(mapper.selectOne(openWrapper()
                        .eq(EmploymentDO::getPersonId, personId)
                        .eq(EmploymentDO::getIsPrimary, true)))
                .map(MybatisEmploymentRepository::toDomain);
    }

    @Override
    public List<Employment> openByOrgUnit(String orgUnitId) {
        return mapper.selectList(openWrapper().eq(EmploymentDO::getOrgUnitId, orgUnitId))
                .stream().map(MybatisEmploymentRepository::toDomain).toList();
    }

    @Override
    public Set<String> openPersonIdsInOrgUnits(Collection<String> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Set.of();
        }
        Set<String> personIds = new LinkedHashSet<>();
        mapper.selectList(openWrapper().select(EmploymentDO::getPersonId).in(EmploymentDO::getOrgUnitId, orgUnitIds))
                .forEach(row -> personIds.add(row.getPersonId()));
        return personIds;
    }

    @Override
    public Set<String> openPersonIdsInPosition(String positionId) {
        // position_id 可空：入参为空时直接返回空集合，避免 eq(null) 退化成捞出全部无岗位任职
        if (positionId == null || positionId.isBlank()) {
            return Set.of();
        }
        Set<String> personIds = new LinkedHashSet<>();
        mapper.selectList(openWrapper().select(EmploymentDO::getPersonId)
                        .eq(EmploymentDO::getPositionId, positionId))
                .forEach(row -> personIds.add(row.getPersonId()));
        return personIds;
    }

    @Override
    public long countOpenByOrgUnit(String orgUnitId) {
        return mapper.selectCount(openWrapper().eq(EmploymentDO::getOrgUnitId, orgUnitId));
    }

    @Override
    public void insert(Employment employment) {
        mapper.insert(toDO(employment));
    }

    @Override
    public void update(Employment employment) {
        mapper.update(null, Wrappers.<EmploymentDO>lambdaUpdate()
                .set(EmploymentDO::getPersonId, employment.personId())
                .set(EmploymentDO::getOrgUnitId, employment.orgUnitId())
                .set(EmploymentDO::getPositionId, employment.positionId())
                .set(EmploymentDO::getIsPrimary, employment.primary())
                .set(EmploymentDO::getStartDate, employment.startDate())
                .set(EmploymentDO::getEndDate, employment.endDate())
                .eq(EmploymentDO::getId, employment.id()));
    }

    @Override
    public void close(String id, LocalDate endDate) {
        mapper.update(null, Wrappers.<EmploymentDO>lambdaUpdate()
                .set(EmploymentDO::getEndDate, endDate)
                .set(EmploymentDO::getIsPrimary, false)
                .eq(EmploymentDO::getId, id));
    }

    @Override
    public void deleteById(String id) {
        mapper.deleteById(id);
    }

    private static LambdaQueryWrapper<EmploymentDO> openWrapper() {
        return Wrappers.<EmploymentDO>lambdaQuery().isNull(EmploymentDO::getEndDate);
    }

    private static EmploymentDO toDO(Employment employment) {
        EmploymentDO row = new EmploymentDO();
        row.setId(employment.id());
        row.setPersonId(employment.personId());
        row.setOrgUnitId(employment.orgUnitId());
        row.setPositionId(employment.positionId());
        row.setIsPrimary(employment.primary());
        row.setStartDate(employment.startDate());
        row.setEndDate(employment.endDate());
        return row;
    }

    private static Employment toDomain(EmploymentDO row) {
        return new Employment(row.getId(), row.getPersonId(), row.getOrgUnitId(), row.getPositionId(),
                Boolean.TRUE.equals(row.getIsPrimary()), row.getStartDate(), row.getEndDate());
    }
}
