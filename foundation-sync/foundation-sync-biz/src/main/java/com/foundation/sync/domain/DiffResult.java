package com.foundation.sync.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.spi.SyncObjectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 差异结果（F-08 BAS-11 §6）：新增 / 修改 / 停用 / 移动四类计数的汇总。
 *
 * <p>预演（dry-run）与落地共用同一份 {@code DiffResult}，口径一致。
 *
 * @param entries 差异条目（可为空）
 */
public record DiffResult(List<DiffEntry> entries) {

    public DiffResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static DiffResult empty() {
        return new DiffResult(List.of());
    }

    public static DiffResult of(List<DiffEntry> entries) {
        return new DiffResult(entries);
    }

    /** 合并两批差异（多对象类型累加）。 */
    public DiffResult merge(DiffResult other) {
        if (other == null || other.entries.isEmpty()) {
            return this;
        }
        if (entries.isEmpty()) {
            return other;
        }
        List<DiffEntry> merged = new ArrayList<>(entries);
        merged.addAll(other.entries);
        return new DiffResult(merged);
    }

    public int countCreated() {
        return count(DiffKind.CREATE);
    }

    public int countUpdated() {
        return count(DiffKind.UPDATE);
    }

    public int countDisabled() {
        return count(DiffKind.DISABLE);
    }

    public int countMoved() {
        return count(DiffKind.MOVE);
    }

    public int count(DiffKind kind) {
        int total = 0;
        for (DiffEntry entry : entries) {
            if (entry.kind() == kind) {
                total++;
            }
        }
        return total;
    }

    /** 按对象类型统计某类差异（删除保护阈值判定用）。 */
    public int count(DiffKind kind, SyncObjectType objectType) {
        int total = 0;
        for (DiffEntry entry : entries) {
            if (entry.kind() == kind && entry.objectType() == objectType) {
                total++;
            }
        }
        return total;
    }

    /** 该对象类型的停用条目。 */
    public List<DiffEntry> disabled(SyncObjectType objectType) {
        return byKindAndType(DiffKind.DISABLE, objectType);
    }

    public List<DiffEntry> byKind(DiffKind kind) {
        List<DiffEntry> matches = new ArrayList<>();
        for (DiffEntry entry : entries) {
            if (entry.kind() == kind) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public List<DiffEntry> byKindAndType(DiffKind kind, SyncObjectType objectType) {
        List<DiffEntry> matches = new ArrayList<>();
        for (DiffEntry entry : entries) {
            if (entry.kind() == kind && entry.objectType() == objectType) {
                matches.add(entry);
            }
        }
        return matches;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int total() {
        return entries.size();
    }
}
