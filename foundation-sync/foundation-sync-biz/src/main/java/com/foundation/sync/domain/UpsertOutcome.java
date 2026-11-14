package com.foundation.sync.domain;

import java.util.List;

/**
 * 幂等落地结果（F-08 BAS-11 §7、§12）。
 *
 * @param created   新增条数
 * @param updated   修改条数
 * @param disabled  停用条数
 * @param moved     移动条数
 * @param skipped   跳过条数（锁定字段全被挡下 / 非权威来源越界 / 唯一键冲突）
 * @param changes   主数据实际变更（发 {@code fdn.org.changed} / {@code fdn.person.*} 用）
 * @param conflicts 冲突条目（越界、锁定字段、唯一键重复）
 * @param logs      运行日志
 */
public record UpsertOutcome(int created,
                            int updated,
                            int disabled,
                            int moved,
                            int skipped,
                            List<LandedChange> changes,
                            List<SyncConflict> conflicts,
                            List<SyncLog> logs) {

    public UpsertOutcome {
        changes = changes == null ? List.of() : List.copyOf(changes);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    /** 实际落地条数（不含跳过）。 */
    public int landed() {
        return created + updated + disabled + moved;
    }

    public boolean hasConflicts() {
        return !conflicts.isEmpty();
    }
}
