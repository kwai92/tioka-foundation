package com.foundation.authz.api;

/**
 * 应用上报维度值的结果摘要（F-06 BAS-06 §接口）。
 *
 * @param created     新增条数
 * @param updated     更新条数（字段确有变化才计入，重复上报为 0）
 * @param deactivated 停用条数
 */
public record DimensionValueSyncResult(int created, int updated, int deactivated) {
}
