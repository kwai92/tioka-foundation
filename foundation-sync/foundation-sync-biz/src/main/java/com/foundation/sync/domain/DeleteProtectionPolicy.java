package com.foundation.sync.domain;

import com.foundation.sync.spi.SyncObjectType;

/**
 * 删除保护阈值（F-08 BAS-11 §6，S15 §10.2、ADR-052 第 5 条、ADR-078 第 7 条）。
 *
 * <p>默认阈值：一次停用人员超过 5% 或超过 50 人、删除（停用）组织单元超过 10 个时暂停待确认；
 * 判定严格大于阈值（边界值不误判）。新增与修改照常落地，仅暂停剩余停用。
 */
public class DeleteProtectionPolicy {

    /** 一次停用人员百分比阈值（默认 5）。 */
    private final int disablePercent;
    /** 一次停用人员绝对数阈值（默认 50）。 */
    private final int disableCount;
    /** 删除（停用）组织单元阈值（默认 10）。 */
    private final int orgUnitDeleteCount;

    public DeleteProtectionPolicy() {
        this(5, 50, 10);
    }

    public DeleteProtectionPolicy(int disablePercent, int disableCount, int orgUnitDeleteCount) {
        this.disablePercent = disablePercent;
        this.disableCount = disableCount;
        this.orgUnitDeleteCount = orgUnitDeleteCount;
    }

    /**
     * 判定是否需暂停待确认。
     *
     * @param diff         差异结果
     * @param totalPersons 本次同步涉及的现存人员总数（百分比基数）
     */
    public DeleteProtectionVerdict evaluate(DiffResult diff, int totalPersons) {
        if (diff == null || diff.isEmpty()) {
            return DeleteProtectionVerdict.pass();
        }
        int disabledOrgs = diff.count(com.foundation.sync.api.dto.DiffKind.DISABLE, SyncObjectType.ORG_UNIT);
        if (disabledOrgs > orgUnitDeleteCount) {
            return DeleteProtectionVerdict.paused();
        }
        int disabledPersons = diff.count(com.foundation.sync.api.dto.DiffKind.DISABLE, SyncObjectType.PERSON);
        if (disabledPersons > disableCount) {
            return DeleteProtectionVerdict.paused();
        }
        if (totalPersons > 0 && (long) disabledPersons * 100 > (long) totalPersons * disablePercent) {
            return DeleteProtectionVerdict.paused();
        }
        return DeleteProtectionVerdict.pass();
    }

    public int disablePercent() {
        return disablePercent;
    }

    public int disableCount() {
        return disableCount;
    }

    public int orgUnitDeleteCount() {
        return orgUnitDeleteCount;
    }
}
