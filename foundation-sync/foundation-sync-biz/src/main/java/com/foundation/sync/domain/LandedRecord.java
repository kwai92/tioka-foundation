package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已落地的本地记录（主数据侧在同步引擎里的投影，F-08 BAS-11 §7）。
 *
 * @param localId           本地主键（{@code per_…} / {@code org_…}）
 * @param key               幂等键（来源 + 外部 ID）
 * @param version           行版本（每次写 +1，事件体据此判重）
 * @param fields            当前内部字段取值（用于差异比对）；<b>值可为 {@code null}</b>
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
        // 刻意不用 Map.copyOf：它**拒绝 null 值**（抛 NPE）。fields 来自
        // MybatisMasterDataPort.personFields 等，会把 fdn_person 的可空列
        // （mobileCipher/emailCipher/mobileHash/emailHash）原样放入——人员没有手机或邮箱是
        // 完全正常的业务态，故 fields 的**值**天然可能为 null。
        // 用 Map.copyOf 会让这类人员在被同步引擎读取（existingPersons / DiffPreviewService.preview
        // 调 listBySource）时抛 NPE，且该异常被 SyncEngine.execute 吞成任务 FAILED，
        // 使删除保护暂停路径永远走不通（由 GfSyncGateIT 在真实 PostgreSQL 上暴露）。
        // 保持 fields 键集完整（不跳过 null 键），只把不可变实现换成容忍 null 值的组合，
        // 使 DiffPreviewService.changedFields 的「本地无该键 vs 本地该键为 null」语义不变。
        fields = fields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
