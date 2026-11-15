package com.foundation.app.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.app.domain.ServiceAccount;
import com.foundation.app.infrastructure.mapper.ServiceAccountMapper;
import com.foundation.app.infrastructure.persistence.ServiceAccountDO;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 服务账号仓储的 MyBatis-Plus 实现（48-CS §4；权限码白名单以 JSON 数组存储）。 */
public class MybatisServiceAccountRepository implements ServiceAccountRepository {

    private final ServiceAccountMapper mapper;
    private final ObjectMapper json;

    public MybatisServiceAccountRepository(ServiceAccountMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    @Override
    public List<ServiceAccount> listByAppId(String appId) {
        return mapper.selectList(Wrappers.<ServiceAccountDO>lambdaQuery()
                        .eq(ServiceAccountDO::getAppId, appId)
                        .orderByAsc(ServiceAccountDO::getName))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<ServiceAccount> findById(String id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public Optional<ServiceAccount> findByName(String appId, String name) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ServiceAccountDO>lambdaQuery()
                .eq(ServiceAccountDO::getAppId, appId)
                .eq(ServiceAccountDO::getName, name))).map(this::toDomain);
    }

    @Override
    public void insert(ServiceAccount serviceAccount, String operator) {
        ServiceAccountDO row = new ServiceAccountDO();
        row.setId(serviceAccount.id());
        row.setAccountId(serviceAccount.accountId());
        row.setAppId(serviceAccount.appId());
        row.setClientId(serviceAccount.clientId());
        row.setName(serviceAccount.name());
        row.setPermissionCodes(toJson(serviceAccount.permissionCodes()));
        row.setOwnerUserId(serviceAccount.ownerUserId());
        row.setStatus(serviceAccount.status());
        row.setExpiresAt(toLocalDateTime(serviceAccount.expiresAt()));
        // 显式设置操作人：AuditMetaObjectHandler 仅在字段为 null 时填充，显式值胜出（语义同原 SQL）
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        mapper.insert(row);
    }

    @Override
    public void updateStatus(String id, String status, String operator) {
        mapper.update(null, Wrappers.<ServiceAccountDO>lambdaUpdate()
                .set(ServiceAccountDO::getStatus, status)
                .set(ServiceAccountDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(ServiceAccountDO::getId, id));
    }

    @Override
    public void updateOwner(String id, String ownerUserId, String operator) {
        mapper.update(null, Wrappers.<ServiceAccountDO>lambdaUpdate()
                .set(ServiceAccountDO::getOwnerUserId, ownerUserId)
                .set(ServiceAccountDO::getUpdateBy, operator)
                .setSql("update_at = now()")
                .eq(ServiceAccountDO::getId, id));
    }

    @Override
    public int disableExpired(Instant now) {
        return mapper.update(null, Wrappers.<ServiceAccountDO>lambdaUpdate()
                .set(ServiceAccountDO::getStatus, "DISABLED")
                .setSql("update_at = now()")
                .eq(ServiceAccountDO::getStatus, "ACTIVE")
                .isNotNull(ServiceAccountDO::getExpiresAt)
                .le(ServiceAccountDO::getExpiresAt, toLocalDateTime(now)));
    }

    private ServiceAccount toDomain(ServiceAccountDO row) {
        return new ServiceAccount(row.getId(), row.getAccountId(), row.getAppId(), row.getClientId(),
                row.getName(), fromJson(row.getPermissionCodes()), row.getOwnerUserId(), row.getStatus(),
                toInstant(row.getExpiresAt()));
    }

    private String toJson(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        try {
            return json.writeValueAsString(codes);
        } catch (IOException e) {
            throw new IllegalStateException("服务账号权限码序列化失败：" + e.getMessage(), e);
        }
    }

    private List<String> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(value, new TypeReference<List<String>>() {
            });
        } catch (IOException e) {
            throw new IllegalStateException("服务账号权限码解析失败：" + e.getMessage(), e);
        }
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.toInstant(ZoneOffset.UTC);
    }
}
