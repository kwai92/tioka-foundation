package com.foundation.common.param.infrastructure;

import com.foundation.common.param.domain.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

/** 参数仓储的 JDBC 实现。 */
public class JdbcParamRepository implements ParamRepository {

    private static final String COLUMNS = "id, app_code, param_key, param_value, secret, description";

    private static final RowMapper<Param> MAPPER = (rs, rowNum) -> new Param(
            rs.getString("id"), rs.getString("app_code"), rs.getString("param_key"),
            rs.getString("param_value"), rs.getBoolean("secret"), rs.getString("description"));

    private final JdbcTemplate jdbc;

    public JdbcParamRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Param> find(String appCode, String paramKey) {
        List<Param> rows = appCode == null
                ? jdbc.query("SELECT " + COLUMNS + " FROM fdn_param WHERE param_key = ? AND app_code IS NULL",
                        MAPPER, paramKey)
                : jdbc.query("SELECT " + COLUMNS + " FROM fdn_param WHERE param_key = ? AND app_code = ?",
                        MAPPER, paramKey, appCode);
        return rows.stream().findFirst();
    }

    @Override
    public void insert(Param param, String operator) {
        jdbc.update("INSERT INTO fdn_param (id, app_code, param_key, param_value, secret, description, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                param.id(), param.appCode(), param.paramKey(), param.storedValue(), param.secret(),
                param.description(), operator);
    }

    @Override
    public void update(Param param, String operator) {
        jdbc.update("UPDATE fdn_param SET param_value = ?, secret = ?, description = ?, "
                        + "update_at = now(), update_by = ? WHERE id = ?",
                param.storedValue(), param.secret(), param.description(), operator, param.id());
    }
}
