package com.foundation.app.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.app.domain.ServiceAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 服务账号仓储的 JDBC 实现（权限码白名单以 JSON 数组存储）。 */
public class JdbcServiceAccountRepository implements ServiceAccountRepository {

    private static final String COLUMNS =
            "id, account_id, app_id, client_id, name, permission_codes, owner_user_id, status, expires_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final RowMapper<ServiceAccount> mapper;

    public JdbcServiceAccountRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
        this.mapper = (rs, rowNum) -> {
            Timestamp expiresAt = rs.getTimestamp("expires_at");
            return new ServiceAccount(rs.getString("id"), rs.getString("account_id"), rs.getString("app_id"),
                    rs.getString("client_id"), rs.getString("name"),
                    fromJson(rs.getString("permission_codes")), rs.getString("owner_user_id"),
                    rs.getString("status"), expiresAt == null ? null : expiresAt.toInstant());
        };
    }

    @Override
    public List<ServiceAccount> listByAppId(String appId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_service_account WHERE app_id = ? ORDER BY name",
                mapper, appId);
    }

    @Override
    public Optional<ServiceAccount> findById(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_service_account WHERE id = ?", mapper, id)
                .stream().findFirst();
    }

    @Override
    public Optional<ServiceAccount> findByName(String appId, String name) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_service_account WHERE app_id = ? AND name = ?",
                        mapper, appId, name)
                .stream().findFirst();
    }

    @Override
    public void insert(ServiceAccount serviceAccount, String operator) {
        jdbc.update("INSERT INTO fdn_service_account (id, account_id, app_id, client_id, name, "
                        + "permission_codes, owner_user_id, status, expires_at, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                serviceAccount.id(), serviceAccount.accountId(), serviceAccount.appId(),
                serviceAccount.clientId(), serviceAccount.name(),
                toJson(serviceAccount.permissionCodes()), serviceAccount.ownerUserId(),
                serviceAccount.status(),
                serviceAccount.expiresAt() == null ? null : Timestamp.from(serviceAccount.expiresAt()),
                operator, operator);
    }

    @Override
    public void updateStatus(String id, String status, String operator) {
        jdbc.update("UPDATE fdn_service_account SET status = ?, update_at = now(), update_by = ? WHERE id = ?",
                status, operator, id);
    }

    @Override
    public void updateOwner(String id, String ownerUserId, String operator) {
        jdbc.update("UPDATE fdn_service_account SET owner_user_id = ?, update_at = now(), update_by = ? "
                + "WHERE id = ?", ownerUserId, operator, id);
    }

    @Override
    public int disableExpired(Instant now) {
        return jdbc.update("UPDATE fdn_service_account SET status = 'DISABLED', update_at = now() "
                + "WHERE status = 'ACTIVE' AND expires_at IS NOT NULL AND expires_at <= ?", Timestamp.from(now));
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
}
