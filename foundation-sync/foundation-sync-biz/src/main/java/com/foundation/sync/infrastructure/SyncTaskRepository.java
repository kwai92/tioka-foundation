package com.foundation.sync.infrastructure;

import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.domain.SyncTask;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 同步任务仓储（F-08 BAS-11 §8 {@code fdn_sync_task}）。 */
public interface SyncTaskRepository {

    void insert(SyncTask task);

    void update(SyncTask task);

    Optional<SyncTask> find(String id);

    List<SyncTask> page(String sourceId, SyncTaskStatus status, Instant from, Instant to,
                        int page, int size);

    long count(String sourceId, SyncTaskStatus status, Instant from, Instant to);
}
