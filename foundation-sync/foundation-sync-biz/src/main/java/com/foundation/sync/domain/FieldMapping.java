package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncObjectType;

/**
 * 字段映射（F-08 BAS-11 §8 {@code fdn_sync_field_mapping}）：外部字段 → 内部字段。
 *
 * @param id            映射 ID（{@code map_…}）
 * @param sourceId      来源 ID
 * @param objectType    对象类型
 * @param externalField 外部字段名
 * @param internalField 内部字段名
 * @param defaultValue  默认值（外部缺失时填充），可空
 * @param transform     转换函数名（{@code FieldConverter}），可空
 * @param required      是否必填
 */
public record FieldMapping(String id,
                           String sourceId,
                           SyncObjectType objectType,
                           String externalField,
                           String internalField,
                           String defaultValue,
                           String transform,
                           boolean required) {
}
