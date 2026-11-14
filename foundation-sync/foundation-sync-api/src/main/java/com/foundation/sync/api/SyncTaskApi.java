package com.foundation.sync.api;

import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncLogView;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTaskView;

import java.time.Instant;
import java.util.List;

/**
 * 同步任务契约（F-08 BAS-11 §10）：任务记录、日志明细、超阈值确认 / 取消与明细下载。
 */
public interface SyncTaskApi {

    /** {@code GET /api/sync/tasks}：任务记录列表（含差异摘要与「能力缺失」标注）。 */
    PageResult<SyncTaskView> tasks(String sourceId, SyncTaskStatus status, Instant from, Instant to,
                                   int page, int size);

    /** {@code GET /api/sync/tasks/{id}}：任务详情。 */
    SyncTaskView task(String id);

    /** {@code GET /api/sync/tasks/{id}/logs}：同步日志明细。 */
    List<SyncLogView> logs(String taskId);

    /** {@code GET /api/sync/tasks/{id}/download}：下载明细（CSV 文本）。 */
    String download(String taskId);

    /** {@code POST /api/sync/tasks/{id}/confirm}：超阈值暂停后确认继续。 */
    SyncTaskView confirm(String id, String operator);

    /** {@code POST /api/sync/tasks/{id}/cancel}：取消进行中 / 暂停的任务。 */
    SyncTaskView cancel(String id, String operator);
}
