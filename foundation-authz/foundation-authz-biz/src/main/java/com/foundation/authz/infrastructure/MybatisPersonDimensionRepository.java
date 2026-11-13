package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.GrantSource;
import com.foundation.authz.domain.PersonDimensionGrant;
import com.foundation.authz.infrastructure.mapper.PersonDimensionMapper;
import com.foundation.authz.infrastructure.persistence.PersonDimensionDO;

import java.util.List;

/** 人员维度授予仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisPersonDimensionRepository implements PersonDimensionRepository {

    private final PersonDimensionMapper mapper;

    public MybatisPersonDimensionRepository(PersonDimensionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PersonDimensionGrant> listByPerson(String personId) {
        return mapper.selectList(Wrappers.<PersonDimensionDO>lambdaQuery()
                        .eq(PersonDimensionDO::getPersonId, personId)
                        .orderByAsc(PersonDimensionDO::getId))
                .stream().map(MybatisPersonDimensionRepository::toDomain).toList();
    }

    @Override
    public List<PersonDimensionGrant> listByPersonAndDimension(String personId, String dimensionId) {
        return mapper.selectList(Wrappers.<PersonDimensionDO>lambdaQuery()
                        .eq(PersonDimensionDO::getPersonId, personId)
                        .eq(PersonDimensionDO::getDimensionId, dimensionId)
                        .orderByAsc(PersonDimensionDO::getId))
                .stream().map(MybatisPersonDimensionRepository::toDomain).toList();
    }

    @Override
    public void insert(PersonDimensionGrant grant) {
        PersonDimensionDO row = new PersonDimensionDO();
        row.setId(grant.id());
        row.setPersonId(grant.personId());
        row.setDimensionId(grant.dimensionId());
        row.setValueCode(grant.valueCode());
        row.setAllValues(grant.allValues());
        row.setSource(grant.source().name());
        mapper.insert(row);
    }

    @Override
    public void deleteByPersonAndDimension(String personId, String dimensionId) {
        // 逻辑删（@TableLogic）
        mapper.delete(Wrappers.<PersonDimensionDO>lambdaQuery()
                .eq(PersonDimensionDO::getPersonId, personId)
                .eq(PersonDimensionDO::getDimensionId, dimensionId));
    }

    private static PersonDimensionGrant toDomain(PersonDimensionDO row) {
        return new PersonDimensionGrant(row.getId(), row.getPersonId(), row.getDimensionId(),
                row.getValueCode(), Boolean.TRUE.equals(row.getAllValues()), GrantSource.of(row.getSource()));
    }
}
