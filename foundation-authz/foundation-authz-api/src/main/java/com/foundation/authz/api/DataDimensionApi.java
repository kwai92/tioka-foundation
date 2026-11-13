package com.foundation.authz.api;

import java.util.List;

/**
 * 数据维度契约（F-06 BAS-06 §接口 {@code /api/fdn/dimensions}）。
 *
 * <p>维度可由清单声明（BAS-05）或管理台新建；{@code appCode} 为空表示全局维度。
 */
public interface DataDimensionApi {

    /** 维度列表（含全局与应用级）。{@code appCode} 为空时只返回全局维度。 */
    List<DataDimensionView> list(String appCode);

    /** 新建维度，返回维度 ID（{@code fdd_{ulid}}）；{@code code} 不可改。 */
    String create(DataDimensionCmd cmd);

    /** 改名称、是否层级；值来源变更需清空既有值。 */
    void update(String appCode, String code, DataDimensionUpdateCmd cmd);

    /**
     * 应用上报增量维护值列表（幂等）：{@code upserts} 新增或更新，{@code deactivations} 停用保留。
     *
     * @return 新增 / 更新 / 停用条数
     */
    DimensionValueSyncResult syncValues(String appCode, String code, DimensionValueSyncCmd cmd);
}
