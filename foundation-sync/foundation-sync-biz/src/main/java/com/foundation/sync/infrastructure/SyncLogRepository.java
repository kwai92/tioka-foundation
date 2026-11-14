package com.foundation.sync.infrastructure;

import com.foundation.sync.domain.SyncLog;

import java.util.List;

/** 同步日志仓储（F-08 BAS-11 §8 {@code fdn_sync_log}）：只追加。 */
public interface SyncLogRepository {

    void appendAll(List<SyncLog> logs);

    List<SyncLog> listByTask(String taskId);
}
