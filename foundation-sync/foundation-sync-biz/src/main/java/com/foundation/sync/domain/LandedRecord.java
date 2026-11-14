package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncKey;

import java.util.Map;

/**
 * 已落地的本地记录（主数据侧在同步引擎里的投影，F-08 BAS-11 §7）。
 *
 * @param localId           本地主键（{@code per_…} / {@code org_…}）
 * @param key               幂等键（来源 + 外部 ID）
 * @param version           行版本（每次写 +1，事件体据此判重）
 * @param fields            当前内部字段取值（用于差异比对）
 * @param disabled          是否已停用
 * @param parentExternalId  上级对象外部 ID（组织单元移动判定），可空
 */
public record LandedRecord(String localId,
                           SyncKey key,
                           long version,
                           Map<String, Object> fields,
                           boolean disabled,
                           String parentExternalId) {

    public LandedRecord {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }
}
