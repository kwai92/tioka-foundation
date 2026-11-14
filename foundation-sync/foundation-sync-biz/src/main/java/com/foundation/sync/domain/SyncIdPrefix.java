package com.foundation.sync.domain;

/** 同步域 ID 前缀（F-08 BAS-11 §硬约束：主键 ULID，带前缀列宽 {@code VARCHAR(32)}）。 */
public final class SyncIdPrefix {

    /** 同步来源（{@code fdn_sync_source}）。 */
    public static final String SOURCE = "src";
    /** 字段映射（{@code fdn_sync_field_mapping}）。 */
    public static final String FIELD_MAPPING = "map";
    /** 同步任务（{@code fdn_sync_task}）。 */
    public static final String TASK = "syn";
    /** 同步日志（{@code fdn_sync_log}）。 */
    public static final String LOG = "slg";
    /** 同步冲突（{@code fdn_sync_conflict}）。 */
    public static final String CONFLICT = "cfl";
    /** SCIM 令牌（BAS-13）。 */
    public static final String SCIM_TOKEN = "sct";

    private SyncIdPrefix() {
    }
}
