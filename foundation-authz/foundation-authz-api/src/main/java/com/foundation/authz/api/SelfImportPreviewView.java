package com.foundation.authz.api;

import java.util.List;

/**
 * 自管一次性导入预览视图（F-05 BAS-27 §自管导入）。
 *
 * @param batchId        批次 ID（{@code mpx_{ulid}}）
 * @param subsystemId    子系统 ID
 * @param status         批次状态
 * @param newRoles       待新增角色数
 * @param newAssignments 待新增授权数
 * @param conflicts      冲突数
 * @param details        明细说明
 */
public record SelfImportPreviewView(String batchId,
                                    String subsystemId,
                                    String status,
                                    int newRoles,
                                    int newAssignments,
                                    int conflicts,
                                    List<String> details) {
}
