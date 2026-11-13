package com.foundation.authz.domain;

import java.util.Objects;

/**
 * 数据维度（F-06 BAS-06 §数据模型 {@code fdn_data_dimension}）。
 *
 * @param id          维度 ID（{@code fdd_{ulid}}）
 * @param appCode     所属应用编码；{@code null} 表示全局维度
 * @param code        维度编码（如 {@code region}/{@code store}），创建后不可改
 * @param name        名称
 * @param valueSource 值来源，见 {@link ValueSource}
 * @param hierarchical 是否层级维度
 */
public record DataDimension(String id,
                            String appCode,
                            String code,
                            String name,
                            ValueSource valueSource,
                            boolean hierarchical) {

    public DataDimension {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(valueSource, "valueSource");
    }

    /** 是否为全局维度（{@code app_code} 为空）。 */
    public boolean global() {
        return appCode == null || appCode.isBlank();
    }
}
