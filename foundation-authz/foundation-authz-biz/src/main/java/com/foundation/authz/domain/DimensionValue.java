package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 维度值（F-06 BAS-06 §数据模型 {@code fdn_dimension_value}）。
 *
 * <p>应用上报的值列表经接口增量维护：新增与重新上报置 {@code active = true}，停用置 {@code active = false}
 * 且保留行（停用不物理删除，便于回查）。
 *
 * @param id          值 ID（{@code fdv_{ulid}}）
 * @param dimensionId 所属维度 ID
 * @param code        值编码
 * @param name        名称
 * @param parentId    层级维度的上级值 ID，可空
 * @param active      是否生效
 */
public record DimensionValue(String id,
                             String dimensionId,
                             String code,
                             String name,
                             String parentId,
                             boolean active) {

    public DimensionValue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
