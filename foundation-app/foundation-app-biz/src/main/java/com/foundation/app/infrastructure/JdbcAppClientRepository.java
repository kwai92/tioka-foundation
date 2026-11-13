package com.foundation.app.infrastructure;

import com.foundation.app.domain.AppClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 应用客户端仓储的 JDBC 实现。 */
public class JdbcAppClientRepository implements AppClientRepository {

    private static final String COLUMNS =
            "id, app_id, client_id, redirect_uris, grant_types, status, secret_rotated_at";

    private static final RowMapper<AppClient> MAPPER = (rs, rowNum) -> {
        Timestamp rotatedAt = rs.getTimestamp("secret_rotated_at");
        return new AppClient(rs.getString("id"), rs.getString("app_id"), rs.getString("client_id"),
                CsvLists.split(rs.getString("redirect_uris")), CsvLists.split(rs.getString("grant_types")),
                rs.getString("status"), rotatedAt == null ? null : rotatedAt.toInstant());
    };

    private final JdbcTemplate jdbc;

    public JdbcAppClientRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<AppClient> listByAppId(String appId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app_client WHERE app_id = ? ORDER BY client_id",
                MAPPER, appId);
    }

    @Override
    public Optional<AppClient> find(String appId, String clientId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app_client WHERE app_id = ? AND client_id = ?",
                        MAPPER, appId, clientId)
                .stream().findFirst();
    }

    @Override
    public Optional<AppClient> findById(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app_client WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public void insert(AppClient client, String operator) {
        jdbc.update("INSERT INTO fdn_app_client (id, app_id, client_id, redirect_uris, grant_types, status, "
                        + "secret_rotated_at, create_by, update_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                client.id(), client.appId(), client.clientId(), CsvLists.join(client.redirectUris()),
                CsvLists.join(client.grantTypes()), client.status(),
                client.secretRotatedAt() == null ? null : Timestamp.from(client.secretRotatedAt()),
                operator, operator);
    }

    @Override
    public void updateStatus(String id, String status, String operator) {
        jdbc.update("UPDATE fdn_app_client SET status = ?, update_at = now(), update_by = ? WHERE id = ?",
                status, operator, id);
    }

    @Override
    public void markSecretRotated(String id, Instant rotatedAt, String operator) {
        jdbc.update("UPDATE fdn_app_client SET secret_rotated_at = ?, update_at = now(), update_by = ? "
                + "WHERE id = ?", Timestamp.from(rotatedAt), operator, id);
    }
}
