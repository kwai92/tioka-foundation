package com.foundation.common.api.dict;

import java.util.Map;

/**
 * 字典项写命令。
 *
 * @param appCode  归属应用；{@code null} 表示全局（用于定位字典类型）
 * @param typeCode 类型编码
 * @param code     项编码（类型内唯一）
 * @param label    展示名
 * @param sortNo   排序号，越小越靠前
 * @param status   {@code ENABLED} / {@code DISABLED}；{@code null} 视为 {@code ENABLED}
 * @param styleTag 样式标签（前端渲染用，可空）
 * @param ext      扩展属性，落 {@code ext_json}
 * @param operator 操作人
 */
public record DictItemCmd(String appCode, String typeCode, String code, String label, int sortNo,
                          String status, String styleTag, Map<String, Object> ext, String operator) {
}
