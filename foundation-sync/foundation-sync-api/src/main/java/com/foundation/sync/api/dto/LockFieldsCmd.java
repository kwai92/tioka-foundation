package com.foundation.sync.api.dto;

import java.util.Set;

/**
 * 锁定 / 解锁本地字段（F-08 BAS-11 §10 {@code PUT /api/persons/{id}/local-locks}、{@code /api/org/units/{id}/local-locks}）。
 *
 * @param fields 目标锁定字段集合（整包替换；空集合即全部解锁）
 */
public record LockFieldsCmd(Set<String> fields) {

    public LockFieldsCmd {
        fields = fields == null ? Set.of() : Set.copyOf(fields);
    }
}
