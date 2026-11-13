package com.foundation.authz.api;

import java.util.List;

/**
 * 应用上报维度值增量维护入参（F-06 BAS-06 §接口 {@code PUT .../values:sync}）。
 *
 * @param upserts       新增或更新项
 * @param deactivations 停用值编码（停用保留，不出现在取值集合中）
 */
public record DimensionValueSyncCmd(List<ValueUpsert> upserts, List<String> deactivations) {

    public DimensionValueSyncCmd {
        upserts = upserts == null ? List.of() : List.copyOf(upserts);
        deactivations = deactivations == null ? List.of() : List.copyOf(deactivations);
    }

    /**
     * 单条上报。
     *
     * @param code       值编码
     * @param name       名称
     * @param parentCode 上级值编码，可空（层级维度）
     */
    public record ValueUpsert(String code, String name, String parentCode) {
    }
}
