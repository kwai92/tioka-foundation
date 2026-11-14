package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.FeatureToggle;
import com.foundation.authz.domain.ToggleScopeType;
import com.foundation.authz.infrastructure.mapper.FeatureToggleMapper;
import com.foundation.authz.infrastructure.persistence.FeatureToggleDO;

import java.util.List;
import java.util.Optional;

/** 功能开关仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisFeatureToggleRepository implements FeatureToggleRepository {

    private final FeatureToggleMapper mapper;

    public MybatisFeatureToggleRepository(FeatureToggleMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<FeatureToggle> list() {
        return mapper.selectList(Wrappers.<FeatureToggleDO>lambdaQuery()
                        .orderByAsc(FeatureToggleDO::getScopeType)
                        .orderByAsc(FeatureToggleDO::getScopeId))
                .stream().map(MybatisFeatureToggleRepository::toDomain).toList();
    }

    @Override
    public Optional<FeatureToggle> find(ToggleScopeType scopeType, String scopeId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<FeatureToggleDO>lambdaQuery()
                .eq(FeatureToggleDO::getScopeType, scopeType.name())
                .eq(FeatureToggleDO::getScopeId, scopeId))).map(MybatisFeatureToggleRepository::toDomain);
    }

    @Override
    public Optional<FeatureToggle> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisFeatureToggleRepository::toDomain);
    }

    @Override
    public void insert(FeatureToggle toggle) {
        FeatureToggleDO row = new FeatureToggleDO();
        row.setId(toggle.id());
        row.setScopeType(toggle.scopeType().name());
        row.setScopeId(toggle.scopeId());
        row.setEnabled(toggle.enabled());
        row.setReason(toggle.reason());
        mapper.insert(row);
    }

    @Override
    public void update(FeatureToggle toggle) {
        FeatureToggleDO row = new FeatureToggleDO();
        row.setId(toggle.id());
        row.setEnabled(toggle.enabled());
        row.setReason(toggle.reason());
        mapper.updateById(row);
    }

    private static FeatureToggle toDomain(FeatureToggleDO row) {
        return new FeatureToggle(row.getId(), ToggleScopeType.of(row.getScopeType()), row.getScopeId(),
                Boolean.TRUE.equals(row.getEnabled()), row.getReason());
    }
}
