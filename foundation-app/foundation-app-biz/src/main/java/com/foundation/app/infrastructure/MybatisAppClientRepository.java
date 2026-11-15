package com.foundation.app.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.app.domain.AppClient;
import com.foundation.app.infrastructure.mapper.AppClientMapper;
import com.foundation.app.infrastructure.persistence.AppClientDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 应用客户端仓储的 MyBatis-Plus 实现（48-CS §4；回调地址/授权类型以逗号分隔文本存储）。 */
public class MybatisAppClientRepository implements AppClientRepository {

    private final AppClientMapper mapper;

    public MybatisAppClientRepository(AppClientMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AppClient> listByAppId(String appId) {
        return mapper.selectList(Wrappers.<AppClientDO>lambdaQuery()
                        .eq(AppClientDO::getAppId, appId)
                        .orderByAsc(AppClientDO::getClientId))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<AppClient> find(String appId, String clientId) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<AppClientDO>lambdaQuery()
                .eq(AppClientDO::getAppId, appId)
                .eq(AppClientDO::getClientId, clientId))).map(this::toDomain);
    }

    @Override
    public Optional<AppClient> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public void insert(AppClient client, String operator) {
        AppClientDO row = new AppClientDO();
        row.setId(client.id());
        row.setAppId(client.appId());
        row.setClientId(client.clientId());
        row.setRedirectUris(CsvLists.join(client.redirectUris()));
        row.setGrantTypes(CsvLists.join(client.grantTypes()));
        row.setStatus(client.status());
        row.setSecretRotatedAt(toLocalDateTime(client.secretRotatedAt()));
        // 显式设置操作人：AuditMetaObjectHandler 仅在字段为 null 时填充，显式值胜出（语义同原 SQL）
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void updateStatus(String id, String status, String operator) {
        mapper.update(null, Wrappers.<AppClientDO>lambdaUpdate()
                .set(AppClientDO::getStatus, status)
                .set(AppClientDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(AppClientDO::getId, id));
    }

    @Override
    public void markSecretRotated(String id, Instant rotatedAt, String operator) {
        mapper.update(null, Wrappers.<AppClientDO>lambdaUpdate()
                .set(AppClientDO::getSecretRotatedAt, toLocalDateTime(rotatedAt))
                .set(AppClientDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(AppClientDO::getId, id));
    }

    private AppClient toDomain(AppClientDO row) {
        return new AppClient(row.getId(), row.getAppId(), row.getClientId(),
                CsvLists.split(row.getRedirectUris()), CsvLists.split(row.getGrantTypes()),
                row.getStatus(), toInstant(row.getSecretRotatedAt()));
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
