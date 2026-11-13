package com.foundation.authz.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 授权（F-05 BAS-05 §领域模型）：把角色授予人员 / 组织单元 / 岗位。
 *
 * @param id                 授权 ID（{@code grt_{ulid}}）
 * @param roleCode           角色编码
 * @param subjectType        见 {@link GrantSubjectType}
 * @param subjectId          授予对象 ID
 * @param includeDescendants 是否含下级（仅 {@link GrantSubjectType#ORG_UNIT} 有意义）
 * @param effectiveFrom      生效时间，{@code null} 表示不限
 * @param effectiveTo        失效时间，{@code null} 表示不限
 * @param source             见 {@link GrantSource}
 */
public record RoleGrant(String id,
                        String roleCode,
                        GrantSubjectType subjectType,
                        String subjectId,
                        boolean includeDescendants,
                        Instant effectiveFrom,
                        Instant effectiveTo,
                        GrantSource source) {

    public RoleGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        source = source == null ? GrantSource.MANUAL : source;
    }

    /** 授权在 {@code at} 时刻是否有效（生效期为空视为不限）。 */
    public boolean effectiveAt(Instant at) {
        if (effectiveFrom != null && at.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !at.isAfter(effectiveTo);
    }
}
