package com.foundation.sync.api.dto;

/**
 * 触发同步 / 差异预演（F-08 BAS-11 §10 {@code POST /api/sync/sources/{id}/run}、{@code /preview}）。
 *
 * @param mode   同步模式（缺省 {@link SyncMode#FULL}）
 * @param dryRun 是否仅预演（{@code preview} 接口恒为 {@code true}）
 */
public record SyncRunCmd(SyncMode mode, Boolean dryRun) {
}
