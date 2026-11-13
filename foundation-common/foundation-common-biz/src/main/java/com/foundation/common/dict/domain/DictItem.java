package com.foundation.common.dict.domain;

import java.util.Map;

/**
 * 字典项。
 *
 * @param id       主键 {@code di_{ulid}}
 * @param typeId   所属字典类型
 * @param code     项编码（类型内唯一）
 * @param label    展示名
 * @param sortNo   排序号
 * @param status   {@code ENABLED} / {@code DISABLED}
 * @param styleTag 样式标签
 * @param ext      扩展属性
 */
public record DictItem(String id, String typeId, String code, String label, int sortNo,
                       String status, String styleTag, Map<String, Object> ext) {
}
