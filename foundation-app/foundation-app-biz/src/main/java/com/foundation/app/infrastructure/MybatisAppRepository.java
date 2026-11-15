package com.foundation.app.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.app.domain.App;
import com.foundation.app.infrastructure.mapper.AppMapper;
import com.foundation.app.infrastructure.persistence.AppDO;

import java.util.List;
import java.util.Optional;

/** 应用仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisAppRepository implements AppRepository {

    private final AppMapper mapper;

    public MybatisAppRepository(AppMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<App> list() {
        return mapper.selectList(Wrappers.<AppDO>lambdaQuery().orderByAsc(AppDO::getCode))
                .stream().map(MybatisAppRepository::toDomain).toList();
    }

    @Override
    public Optional<App> find(String code) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AppDO>lambdaQuery().eq(AppDO::getCode, code))).map(MybatisAppRepository::toDomain);
    }

    @Override
    public Optional<App> findById(String id) {
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AppDO>lambdaQuery().eq(AppDO::getId, id))).map(MybatisAppRepository::toDomain);
    }

    @Override
    public void insert(App app, String operator) {
        AppDO row = new AppDO();
        row.setId(app.id());
        row.setCode(app.code());
        row.setName(app.name());
        row.setIcon(app.icon());
        row.setAccessUrl(app.accessUrl());
        row.setKind(app.kind());
        row.setOwnerUserId(app.ownerUserId());
        row.setStatus(app.status());
        row.setManifestVersion(app.manifestVersion());
        // 显式设置操作人：AuditMetaObjectHandler 仅在字段为 null 时填充，显式值胜出（语义同原 SQL）
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void update(App app, String operator) {
        // code 不可改：更新语句不含 code 列
        mapper.update(null, Wrappers.<AppDO>lambdaUpdate()
                .set(AppDO::getName, app.name())
                .set(AppDO::getIcon, app.icon())
                .set(AppDO::getAccessUrl, app.accessUrl())
                .set(AppDO::getOwnerUserId, app.ownerUserId())
                .set(AppDO::getStatus, app.status())
                .set(AppDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(AppDO::getId, app.id()));
    }

    @Override
    public void updateManifestVersion(String id, long version, String operator) {
        mapper.update(null, Wrappers.<AppDO>lambdaUpdate()
                .set(AppDO::getManifestVersion, version)
                .set(AppDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(AppDO::getId, id));
    }

    private static App toDomain(AppDO row) {
        return new App(row.getId(), row.getCode(), row.getName(), row.getIcon(), row.getAccessUrl(),
                row.getKind(), row.getOwnerUserId(), row.getStatus(),
                row.getManifestVersion() == null ? 0L : row.getManifestVersion());
    }
}
