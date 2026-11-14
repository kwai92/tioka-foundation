package com.foundation.sync.api;

import com.foundation.sync.api.dto.DiffPreviewView;
import com.foundation.sync.api.dto.FieldMappingReplaceCmd;
import com.foundation.sync.api.dto.FieldMappingView;
import com.foundation.sync.api.dto.SyncRunCmd;
import com.foundation.sync.api.dto.SyncSourceCreateCmd;
import com.foundation.sync.api.dto.SyncSourceUpdateCmd;
import com.foundation.sync.api.dto.SyncSourceView;
import com.foundation.sync.api.dto.SyncTaskView;
import com.foundation.sync.spi.ProbeResult;

import java.util.List;

/**
 * 同步来源管理契约（F-08 BAS-11 §10）：来源登记、能力探测、字段映射、差异预演与手动触发。
 *
 * <p>管理台与订阅方只依赖本接口；实现位于 {@code foundation-sync-biz}。
 */
public interface SyncSourceApi {

    /** {@code GET /api/sync/sources}：来源列表（含能力矩阵与状态）。 */
    List<SyncSourceView> listSources();

    /** {@code POST /api/sync/sources}：新建来源（{@code Idempotency-Key}）。 */
    SyncSourceView createSource(SyncSourceCreateCmd cmd, String operator);

    /** {@code PUT /api/sync/sources/{id}}：修改（含权威来源切换、启用 / 停用）。 */
    SyncSourceView updateSource(String id, SyncSourceUpdateCmd cmd, String operator);

    /** {@code POST /api/sync/sources/{id}/probe}：连通性与能力探测（逐项结果）。 */
    ProbeResult probe(String id);

    /** {@code GET /api/sync/sources/{id}/field-mappings}：字段映射查询。 */
    List<FieldMappingView> fieldMappings(String sourceId);

    /** {@code PUT /api/sync/sources/{id}/field-mappings}：字段映射全量替换。 */
    List<FieldMappingView> replaceFieldMappings(String sourceId, FieldMappingReplaceCmd cmd, String operator);

    /** {@code POST /api/sync/sources/{id}/preview}：差异预演（dry-run），不落地。 */
    DiffPreviewView preview(String sourceId, SyncRunCmd cmd, String operator);

    /** {@code POST /api/sync/sources/{id}/run}：手动触发同步。 */
    SyncTaskView run(String sourceId, SyncRunCmd cmd, String operator);
}
