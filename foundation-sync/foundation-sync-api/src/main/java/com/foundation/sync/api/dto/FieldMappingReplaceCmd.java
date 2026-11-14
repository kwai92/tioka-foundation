package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SyncObjectType;

import java.util.List;

/**
 * 字段映射全量替换（F-08 BAS-11 §10 {@code PUT /api/sync/sources/{id}/field-mappings}）。
 *
 * <p>整包替换：提交列表中未出现的映射被删除。
 *
 * @param mappings 目标映射集合
 */
public record FieldMappingReplaceCmd(List<Item> mappings) {

    public FieldMappingReplaceCmd {
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }

    /**
     * 单条映射定义（无 ID，由服务端生成）。
     */
    public record Item(SyncObjectType objectType,
                       String externalField,
                       String internalField,
                       String defaultValue,
                       String transform,
                       boolean required) {
    }
}
