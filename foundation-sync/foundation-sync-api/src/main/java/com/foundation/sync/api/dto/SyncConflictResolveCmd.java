package com.foundation.sync.api.dto;

/**
 * 处理同步冲突（F-08 BAS-11 §10 {@code POST /api/sync/conflicts/{id}/resolve}）。
 *
 * @param note 处理说明，可空
 */
public record SyncConflictResolveCmd(String note) {
}
