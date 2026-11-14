package com.foundation.sync.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 本地字段锁定（F-08 BAS-11 §5）。
 *
 * <p>被标记为「本地锁定」的字段不被任何来源覆盖；落地时跳过这些字段，并记为一条
 * {@code LOCKED_FIELD} 冲突（可查、不告警）。锁定位持久化在
 * {@code fdn_person.local_locked_fields} / {@code fdn_org_unit.local_locked_fields}，
 * 以逗号分隔的字段名存放。
 */
public class LocalLockResolver {

    /** 变更字段与本地锁定字段的交集（即被挡下的字段）。 */
    public Set<String> lockedAmong(Set<String> candidateFields, Set<String> lockedFields) {
        if (candidateFields == null || candidateFields.isEmpty() || lockedFields == null || lockedFields.isEmpty()) {
            return Set.of();
        }
        Set<String> locked = new LinkedHashSet<>();
        for (String field : candidateFields) {
            if (lockedFields.contains(field)) {
                locked.add(field);
            }
        }
        return locked;
    }

    /** 从候选字段取值中剔除锁定字段，返回可落地部分。 */
    public Map<String, Object> filterFields(Map<String, Object> fields, Set<String> lockedFields) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (fields == null) {
            return filtered;
        }
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            if (lockedFields != null && lockedFields.contains(entry.getKey())) {
                continue;
            }
            filtered.put(entry.getKey(), entry.getValue());
        }
        return filtered;
    }

    /** 解析逗号分隔字段名（空串 → 空集）。 */
    public Set<String> parse(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> fields = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                fields.add(trimmed);
            }
        }
        return fields.isEmpty() ? Set.of() : Collections.unmodifiableSet(fields);
    }

    /** 序列化为逗号分隔字段名（保持插入顺序）。 */
    public String serialize(Set<String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        return String.join(",", fields);
    }
}
