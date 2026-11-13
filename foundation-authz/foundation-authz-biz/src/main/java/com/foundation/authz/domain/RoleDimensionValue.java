package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 角色维度取值（F-06 BAS-06 §数据模型 {@code fdn_role_dimension_value}）。
 *
 * <p>{@code allValues = true} 表示该维度「全部」，此时 {@code valueCode} 为空。
 *
 * @param id          行 ID（{@code frv_{ulid}}）
 * @param roleCode    角色编码
 * @param dimensionId 维度 ID
 * @param valueCode   值编码；{@code allValues} 时为空
 * @param allValues   是否为「全部」
 */
public record RoleDimensionValue(String id,
                                 String roleCode,
                                 String dimensionId,
                                 String valueCode,
                                 boolean allValues) {

    public RoleDimensionValue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roleCode, "roleCode");
        Objects.requireNonNull(dimensionId, "dimensionId");
    }
}
