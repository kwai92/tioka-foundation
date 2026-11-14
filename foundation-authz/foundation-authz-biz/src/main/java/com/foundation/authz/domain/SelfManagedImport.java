package com.foundation.authz.domain;

/**
 * 自管一次性导入批次（F-05 BAS-27 §自管导入）。
 *
 * @param id          批次 ID（{@code mpx_{ulid}}）
 * @param subsystemId 子系统 ID
 * @param status      状态，见 {@link SelfImportStatus}
 * @param summary     摘要说明，可空
 */
public record SelfManagedImport(String id,
                                String subsystemId,
                                SelfImportStatus status,
                                String summary) {

    /** 副本：标记为已应用。 */
    public SelfManagedImport withApplied(String newSummary) {
        return new SelfManagedImport(id, subsystemId, SelfImportStatus.APPLIED, newSummary);
    }

    /** 副本：标记为已丢弃。 */
    public SelfManagedImport withDiscarded() {
        return new SelfManagedImport(id, subsystemId, SelfImportStatus.DISCARDED, summary);
    }
}
