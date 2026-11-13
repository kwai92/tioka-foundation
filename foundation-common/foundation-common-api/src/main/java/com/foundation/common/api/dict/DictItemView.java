package com.foundation.common.api.dict;

import java.util.Map;

/**
 * 字典项视图（查询返回）。
 *
 * @param id       主键 {@code di_{ulid}}
 * @param typeCode 所属类型编码
 * @param appCode  归属应用；{@code null} 表示全局
 * @param code     项编码
 * @param label    展示名
 * @param sortNo   排序号
 * @param status   状态
 * @param styleTag 样式标签
 * @param ext      扩展属性
 */
public record DictItemView(String id, String typeCode, String appCode, String code, String label,
                           int sortNo, String status, String styleTag, Map<String, Object> ext) {
}
