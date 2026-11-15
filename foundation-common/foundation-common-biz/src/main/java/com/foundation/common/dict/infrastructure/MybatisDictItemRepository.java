package com.foundation.common.dict.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.dict.domain.DictItem;
import com.foundation.common.dict.infrastructure.mapper.DictItemMapper;
import com.foundation.common.dict.infrastructure.persistence.DictItemDO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 字典项仓储的 MyBatis-Plus 实现（48-CS §4；{@code ext_json} 由 Jackson 与 {@code Map} 互转）。 */
public class MybatisDictItemRepository implements DictItemRepository {

    private final DictItemMapper mapper;
    private final ObjectMapper objectMapper;

    public MybatisDictItemRepository(DictItemMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<DictItem> listEnabled(String typeId) {
        return mapper.selectList(Wrappers.<DictItemDO>lambdaQuery()
                        .eq(DictItemDO::getTypeId, typeId)
                        .eq(DictItemDO::getStatus, "ENABLED")
                        .orderByAsc(DictItemDO::getSortNo)
                        .orderByAsc(DictItemDO::getCode))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public Optional<DictItem> find(String typeId, String code) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<DictItemDO>lambdaQuery()
                .eq(DictItemDO::getTypeId, typeId)
                .eq(DictItemDO::getCode, code))).map(this::toDomain);
    }

    @Override
    public void insert(DictItem item) {
        DictItemDO row = new DictItemDO();
        row.setId(item.id());
        row.setTypeId(item.typeId());
        row.setCode(item.code());
        row.setLabel(item.label());
        row.setSortNo(item.sortNo());
        row.setStatus(item.status());
        row.setStyleTag(item.styleTag());
        row.setExtJson(toJson(item.ext()));
        mapper.insert(row);
    }

    @Override
    public void update(DictItem item) {
        // ext_json 是 jsonb 列：Wrapper.set 不会带上 DO 的 typeHandler，须显式 CAST（原 SQL 为 ?::jsonb）
        mapper.update(null, Wrappers.<DictItemDO>lambdaUpdate()
                .set(DictItemDO::getLabel, item.label())
                .set(DictItemDO::getSortNo, item.sortNo())
                .set(DictItemDO::getStatus, item.status())
                .set(DictItemDO::getStyleTag, item.styleTag())
                .setSql("ext_json = cast({0} as jsonb)", toJson(item.ext()))
                .setSql("update_at = now()")
                .eq(DictItemDO::getId, item.id()));
    }

    private DictItem toDomain(DictItemDO row) {
        return new DictItem(row.getId(), row.getTypeId(), row.getCode(), row.getLabel(),
                row.getSortNo() == null ? 0 : row.getSortNo(), row.getStatus(), row.getStyleTag(),
                fromJson(row.getExtJson()));
    }

    private String toJson(Map<String, Object> ext) {
        if (ext == null || ext.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(ext);
        } catch (JsonProcessingException ex) {
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
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("字典项扩展属性无法反序列化：" + json, ex);
        }
    }
}
