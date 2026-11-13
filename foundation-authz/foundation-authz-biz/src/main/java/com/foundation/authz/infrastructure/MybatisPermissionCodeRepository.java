package com.foundation.authz.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.authz.domain.LifecycleStatus;
import com.foundation.authz.domain.PermissionCode;
import com.foundation.authz.domain.RiskLevel;
import com.foundation.authz.infrastructure.mapper.PermissionCodeMapper;
import com.foundation.authz.infrastructure.persistence.PermissionCodeDO;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 权限码仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisPermissionCodeRepository implements PermissionCodeRepository {

    private final PermissionCodeMapper mapper;

    public MybatisPermissionCodeRepository(PermissionCodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PermissionCode> listByApp(String appId) {
        return mapper.selectList(Wrappers.<PermissionCodeDO>lambdaQuery()
                        .eq(PermissionCodeDO::getAppId, appId)
                        .orderByAsc(PermissionCodeDO::getCode))
                .stream().map(MybatisPermissionCodeRepository::toDomain).toList();
    }

    @Override
    public List<PermissionCode> listActive() {
        return mapper.selectList(Wrappers.<PermissionCodeDO>lambdaQuery()
                        .eq(PermissionCodeDO::getStatus, LifecycleStatus.ACTIVE)
                        .orderByAsc(PermissionCodeDO::getCode))
                .stream().map(MybatisPermissionCodeRepository::toDomain).toList();
    }

    @Override
    public Optional<PermissionCode> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(MybatisPermissionCodeRepository::toDomain);
    }

    @Override
    public void insert(PermissionCode permissionCode) {
        PermissionCodeDO row = new PermissionCodeDO();
        row.setId(permissionCode.id());
        row.setAppId(permissionCode.appId());
        row.setCode(permissionCode.code());
        row.setName(permissionCode.name());
        row.setRiskLevel(permissionCode.riskLevel().name());
        row.setStatus(permissionCode.status());
        row.setFirstSeenAt(permissionCode.firstSeenAt());
        row.setOfflineAt(permissionCode.offlineAt());
        mapper.insert(row);
    }

    @Override
    public void updateFromManifest(PermissionCode permissionCode) {
        mapper.update(null, Wrappers.<PermissionCodeDO>lambdaUpdate()
                .set(PermissionCodeDO::getName, permissionCode.name())
                .set(PermissionCodeDO::getRiskLevel, permissionCode.riskLevel().name())
                .set(PermissionCodeDO::getStatus, LifecycleStatus.ACTIVE)
                .set(PermissionCodeDO::getOfflineAt, null)
                .eq(PermissionCodeDO::getId, permissionCode.id()));
    }

    @Override
    public void offline(String id, Instant offlineAt) {
        mapper.update(null, Wrappers.<PermissionCodeDO>lambdaUpdate()
                .set(PermissionCodeDO::getStatus, LifecycleStatus.OFFLINE)
                .set(PermissionCodeDO::getOfflineAt, offlineAt)
                .eq(PermissionCodeDO::getId, id));
    }

    private static PermissionCode toDomain(PermissionCodeDO row) {
        return new PermissionCode(row.getId(), row.getAppId(), row.getCode(), row.getName(),
                RiskLevel.of(row.getRiskLevel()), row.getStatus(), row.getFirstSeenAt(), row.getOfflineAt());
    }
}
