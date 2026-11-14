package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.AuthMode;
import com.foundation.authz.domain.Subsystem;
import com.foundation.authz.infrastructure.mapper.SubsystemMapper;
import com.foundation.authz.infrastructure.persistence.SubsystemDO;

import java.util.List;
import java.util.Optional;

/** 子系统仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisSubsystemRepository implements SubsystemRepository {

    private final SubsystemMapper mapper;

    public MybatisSubsystemRepository(SubsystemMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Subsystem> list() {
        return mapper.selectList(Wrappers.<SubsystemDO>lambdaQuery()
                        .orderByAsc(SubsystemDO::getId))
                .stream().map(MybatisSubsystemRepository::toDomain).toList();
    }

    @Override
    public Optional<Subsystem> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisSubsystemRepository::toDomain);
    }

    @Override
    public Optional<Subsystem> findByAppId(String appId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<SubsystemDO>lambdaQuery()
                .eq(SubsystemDO::getAppId, appId))).map(MybatisSubsystemRepository::toDomain);
    }

    @Override
    public void insert(Subsystem subsystem) {
        SubsystemDO row = new SubsystemDO();
        row.setId(subsystem.id());
        row.setAppId(subsystem.appId());
        row.setAuthMode(subsystem.authMode().name());
        row.setPortalVisible(subsystem.portalVisible());
        row.setAdminUserId(subsystem.adminUserId());
        mapper.insert(row);
    }

    @Override
    public void updateAuthMode(String id, AuthMode authMode) {
        mapper.update(null, Wrappers.<SubsystemDO>lambdaUpdate()
                .set(SubsystemDO::getAuthMode, authMode.name())
                .eq(SubsystemDO::getId, id));
    }

    private static Subsystem toDomain(SubsystemDO row) {
        return new Subsystem(row.getId(), row.getAppId(), AuthMode.of(row.getAuthMode()),
                row.getPortalVisible() == null || row.getPortalVisible(), row.getAdminUserId());
    }
}
