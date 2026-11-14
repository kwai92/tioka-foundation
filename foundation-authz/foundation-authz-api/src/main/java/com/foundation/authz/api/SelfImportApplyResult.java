package com.foundation.authz.api;

/**
 * 自管一次性导入应用结果（F-05 BAS-27 §自管导入）。
 *
 * @param batchId            批次 ID
 * @param subsystemId        子系统 ID
 * @param authMode           导入后的授权模式
 * @param createdRoles       新建角色数
 * @param createdAssignments 新建授权数
 * @param alreadyApplied     是否已应用（幂等）
 */
public record SelfImportApplyResult(String batchId,
                                    String subsystemId,
                                    String authMode,
                                    int createdRoles,
                                    int createdAssignments,
                                    boolean alreadyApplied) {
}
