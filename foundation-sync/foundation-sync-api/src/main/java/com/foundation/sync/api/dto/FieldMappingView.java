package com.foundation.sync.api.dto;

import com.foundation.sync.spi.SyncObjectType;

/**
 * 字段映射视图（F-08 BAS-11 §8 {@code fdn_sync_field_mapping}）。
 *
 * @param id            映射 ID（{@code map_…}）
 * @param objectType    对象类型
 * @param externalField 外部字段名
 * @param internalField 内部字段名
 * @param defaultValue  默认值，可空
 * @param transform     转换函数名（{@code FieldConverter}），可空
 * @param required      是否必填
 */
public record FieldMappingView(String id,
                               SyncObjectType objectType,
                               String externalField,
                               String internalField,
                               String defaultValue,
                               String transform,
                               boolean required) {
}
