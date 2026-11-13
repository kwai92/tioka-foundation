package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 人员维度授予（F-06 BAS-06 §数据模型 {@code fdn_person_dimension}）。
 *
 * <p>「某人额外负责华东区」这类不走角色的授予；{@link GrantSource#MANUAL} 不被 {@code RULE}/{@code SYNC} 覆盖。
 *
 * @param id          行 ID（{@code fpd_{ulid}}）
 * @param personId    人员 ID
 * @param dimensionId 维度 ID
 * @param valueCode   值编码；{@code allValues} 时为空
 * @param allValues   是否为「全部」
 * @param source      授予来源，见 {@link GrantSource}
 */
public record PersonDimensionGrant(String id,
                                   String personId,
                                   String dimensionId,
                                   String valueCode,
                                   boolean allValues,
                                   GrantSource source) {

    public PersonDimensionGrant {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(personId, "personId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        source = source == null ? GrantSource.MANUAL : source;
    }
}
