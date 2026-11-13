package com.foundation.common.dict.infrastructure;

import com.foundation.common.dict.domain.DictType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

/** 字典类型仓储的 JDBC 实现。 */
public class JdbcDictTypeRepository implements DictTypeRepository {

    private static final String COLUMNS = "id, app_code, code, name, status";

    private static final RowMapper<DictType> MAPPER = (rs, rowNum) -> new DictType(
            rs.getString("id"), rs.getString("app_code"), rs.getString("code"),
            rs.getString("name"), rs.getString("status"));

    private final JdbcTemplate jdbc;

    public JdbcDictTypeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<DictType> find(String appCode, String code) {
        List<DictType> rows = appCode == null
                ? jdbc.query("SELECT " + COLUMNS + " FROM fdn_dict_type WHERE code = ? AND app_code IS NULL",
                        MAPPER, code)
                : jdbc.query("SELECT " + COLUMNS + " FROM fdn_dict_type WHERE code = ? AND app_code = ?",
                        MAPPER, code, appCode);
        return rows.stream().findFirst();
    }

    @Override
    public void insert(DictType type, String operator) {
        jdbc.update("INSERT INTO fdn_dict_type (id, code, name, app_code, status, create_by, update_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                type.id(), type.code(), type.name(), type.appCode(), type.status(), operator, operator);
    }

    @Override
    public void update(DictType type, String operator) {
        jdbc.update("UPDATE fdn_dict_type SET name = ?, status = ?, update_at = now(), update_by = ? WHERE id = ?",
                type.name(), type.status(), operator, type.id());
    }
}
