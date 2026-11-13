package com.foundation.app.infrastructure;

import com.foundation.app.domain.App;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

/** 应用仓储的 JDBC 实现。 */
public class JdbcAppRepository implements AppRepository {

    private static final String COLUMNS =
            "id, code, name, icon, access_url, kind, owner_user_id, status, manifest_version";

    private static final RowMapper<App> MAPPER = (rs, rowNum) -> new App(
            rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("icon"),
            rs.getString("access_url"), rs.getString("kind"), rs.getString("owner_user_id"),
            rs.getString("status"), rs.getLong("manifest_version"));

    private final JdbcTemplate jdbc;

    public JdbcAppRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<App> list() {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app ORDER BY code", MAPPER);
    }

    @Override
    public Optional<App> find(String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app WHERE code = ?", MAPPER, code)
                .stream().findFirst();
    }

    @Override
    public Optional<App> findById(String id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_app WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    @Override
    public void insert(App app, String operator) {
        jdbc.update("INSERT INTO fdn_app (id, code, name, icon, access_url, kind, owner_user_id, status, "
                        + "manifest_version, create_by, update_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                app.id(), app.code(), app.name(), app.icon(), app.accessUrl(), app.kind(),
                app.ownerUserId(), app.status(), app.manifestVersion(), operator, operator);
    }

    @Override
    public void update(App app, String operator) {
        // code 不可改：更新语句不含 code 列
        jdbc.update("UPDATE fdn_app SET name = ?, icon = ?, access_url = ?, owner_user_id = ?, status = ?, "
                        + "update_at = now(), update_by = ? WHERE id = ?",
                app.name(), app.icon(), app.accessUrl(), app.ownerUserId(), app.status(), operator, app.id());
    }

    @Override
    public void updateManifestVersion(String id, long version, String operator) {
        jdbc.update("UPDATE fdn_app SET manifest_version = ?, update_at = now(), update_by = ? WHERE id = ?",
                version, operator, id);
    }
}
