package com.foundation.sync.infrastructure;

import com.foundation.sync.domain.SyncConflict;

import java.util.List;
import java.util.Optional;

/** 同步冲突仓储（F-08 BAS-11 §8 {@code fdn_sync_conflict}）。 */
public interface SyncConflictRepository {

    void insertAll(List<SyncConflict> conflicts);

    Optional<SyncConflict> find(String id);

    void markResolved(String id, String operator);

    List<SyncConflict> page(Boolean resolved, int page, int size);

    long count(Boolean resolved);
}
