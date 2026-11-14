package com.foundation.sync.api.dto;

import java.util.List;

/**
 * 差异预演视图（F-08 BAS-11 §6、§10 {@code POST /api/sync/sources/{id}/preview}）。
 *
 * @param sourceId          来源 ID
 * @param taskId            预演任务 ID（{@code syn_…}，{@code dry_run=TRUE}）
 * @param created           新增计数
 * @param updated           修改计数
 * @param disabled          停用计数
 * @param moved             移动计数
 * @param paused            是否因删除保护阈值暂停
 * @param pauseReason       暂停原因，可空
 * @param capabilityMissing 是否含「能力缺失」标注
 * @param items             明细（截断前的全量条目由下载接口提供）
 */
public record DiffPreviewView(String sourceId,
                              String taskId,
                              int created,
                              int updated,
                              int disabled,
                              int moved,
                              boolean paused,
                              SyncPauseReason pauseReason,
                              boolean capabilityMissing,
                              List<DiffItemView> items) {
}
