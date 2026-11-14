package com.foundation.sync.application;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.api.SyncTaskApi;
import com.foundation.sync.api.dto.PageResult;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncLogView;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTaskView;
import com.foundation.sync.domain.SyncLog;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.domain.SyncTask;
import com.foundation.sync.infrastructure.SyncLogRepository;
import com.foundation.sync.infrastructure.SyncSourceRepository;
import com.foundation.sync.infrastructure.SyncTaskRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 同步任务应用服务（F-08 BAS-11 §10）：任务记录、日志明细、超阈值确认 / 取消与明细下载。
 */
public class SyncTaskService implements SyncTaskApi {

    private static final String CSV_HEADER = "level,objectType,externalId,action,message,createAt";

    private final SyncTaskRepository tasks;
    private final SyncLogRepository logs;
    private final SyncSourceRepository sources;
    private final SyncEngine engine;

    public SyncTaskService(SyncTaskRepository tasks, SyncLogRepository logs, SyncSourceRepository sources,
                           SyncEngine engine) {
        this.tasks = tasks;
        this.logs = logs;
        this.sources = sources;
        this.engine = engine;
    }

    @Override
    public PageResult<SyncTaskView> tasks(String sourceId, SyncTaskStatus status, Instant from, Instant to,
                                          int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 20 : size;
        List<SyncTaskView> items = tasks.page(sourceId, status, from, to, safePage, safeSize).stream()
                .map(task -> SyncViews.task(task, sourceCode(task.sourceId()))).toList();
        long total = tasks.count(sourceId, status, from, to);
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public SyncTaskView task(String id) {
        SyncTask task = requireTask(id);
        return SyncViews.task(task, sourceCode(task.sourceId()));
    }

    @Override
    public List<SyncLogView> logs(String taskId) {
        requireTask(taskId);
        return logs.listByTask(taskId).stream().map(SyncViews::log).toList();
    }

    @Override
    public String download(String taskId) {
        requireTask(taskId);
        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        for (SyncLog log : logs.listByTask(taskId)) {
            csv.append(cell(log.level() == null ? null : log.level().name())).append(',')
                    .append(cell(log.objectType() == null ? null : log.objectType().name())).append(',')
                    .append(cell(log.externalId())).append(',')
                    .append(cell(log.action() == null ? null : log.action().name())).append(',')
                    .append(cell(log.message())).append(',')
                    .append(cell(log.createAt() == null ? null : log.createAt().toString())).append('\n');
        }
        return csv.toString();
    }

    @Override
    @Transactional
    public SyncTaskView confirm(String id, String operator) {
        SyncTask task = requireTask(id);
        if (task.status() != SyncTaskStatus.PAUSED || task.pauseReason() != SyncPauseReason.THRESHOLD) {
            throw new BizException(ErrorCode.CONFLICT, "任务未因删除保护阈值暂停，无需确认: " + task.status());
        }
        SyncSource source = sources.find(task.sourceId())
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "同步来源不存在: " + task.sourceId()));
        SyncEngine.SyncOutcome outcome = engine.confirm(source, task, operator);
        return SyncViews.task(outcome.task(), source.code());
    }

    @Override
    @Transactional
    public SyncTaskView cancel(String id, String operator) {
        SyncTask task = requireTask(id);
        if (task.status().isTerminal()) {
            throw new BizException(ErrorCode.CONFLICT, "任务已结束，无法取消: " + task.status());
        }
        SyncTask cancelled = task.withStatus(SyncTaskStatus.FAILED, null, Instant.now());
        tasks.update(cancelled);
        List<SyncLog> notes = new ArrayList<>();
        notes.add(SyncLog.warn(id, null, null, SyncAction.SKIP, "任务被取消，操作人：" + operator));
        logs.appendAll(notes);
        return SyncViews.task(cancelled, sourceCode(task.sourceId()));
    }

    // ------------------------------------------------------------------ 内部

    private SyncTask requireTask(String id) {
        return tasks.find(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "同步任务不存在: " + id));
    }

    private String sourceCode(String sourceId) {
        return sources.find(sourceId).map(SyncSource::code).orElse(null);
    }

    /** CSV 单元格转义：含逗号 / 引号 / 换行时整体加引号并把引号翻倍。 */
    private static String cell(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
