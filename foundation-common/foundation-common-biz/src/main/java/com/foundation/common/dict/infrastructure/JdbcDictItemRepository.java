package com.foundation.common.dict.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.dict.domain.DictItem;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 字典项仓储的 JDBC 实现（{@code ext_json} 由 Jackson 与 {@code Map} 互转）。 */
public class JdbcDictItemRepository implements DictItemRepository {

    private static final String COLUMNS = "id, type_id, code, label, sort_no, status, style_tag, ext_json";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcDictItemRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DictItem> listEnabled(String typeId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_dict_item "
                + "WHERE type_id = ? AND status = 'ENABLED' ORDER BY sort_no, code", this::mapRow, typeId);
    }

    @Override
    public Optional<DictItem> find(String typeId, String code) {
        return jdbc.query("SELECT " + COLUMNS + " FROM fdn_dict_item WHERE type_id = ? AND code = ?",
                this::mapRow, typeId, code).stream().findFirst();
    }

    @Override
    public void insert(DictItem item) {
        jdbc.update("INSERT INTO fdn_dict_item (id, type_id, code, label, sort_no, status, style_tag, ext_json) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                item.id(), item.typeId(), item.code(), item.label(), item.sortNo(), item.status(),
                item.styleTag(), toJson(item.ext()));
    }

    @Override
    public void update(DictItem item) {
        jdbc.update("UPDATE fdn_dict_item SET label = ?, sort_no = ?, status = ?, style_tag = ?, "
                        + "ext_json = ?::jsonb, update_at = now() WHERE id = ?",
                item.label(), item.sortNo(), item.status(), item.styleTag(), toJson(item.ext()), item.id());
    }

    private DictItem mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new DictItem(rs.getString("id"), rs.getString("type_id"), rs.getString("code"),
                rs.getString("label"), rs.getInt("sort_no"), rs.getString("status"),
                rs.getString("style_tag"), fromJson(rs.getString("ext_json")));
    }

    private String toJson(Map<String, Object> ext) {
        if (ext == null || ext.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ext);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalArgumentException("字典项扩展属性无法序列化", ex);
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("字典项扩展属性无法反序列化：" + json, ex);
        }
    }
}
