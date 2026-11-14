package com.foundation.authz.api;

/**
 * 自管一次性导入明细项命令（F-05 BAS-27 §自管导入）。
 *
 * @param kind        类型：{@code ROLE} / {@code ASSIGNMENT}
 * @param externalKey 外部唯一键
 * @param payload     明细载荷（JSON）
 */
public record SelfImportItemCmd(String kind, String externalKey, String payload) {
}
