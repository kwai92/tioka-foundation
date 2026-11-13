package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.DataDimension;

import java.util.List;
import java.util.Optional;

/** 数据维度仓储（F-06 BAS-06 §数据模型 {@code fdn_data_dimension}）。 */
public interface DataDimensionRepository {

    /** 维度列表：{@code appCode} 为空只返回全局维度，否则返回全局 + 该应用维度（按编码升序）。 */
    List<DataDimension> list(String appCode);

    /** 全部维度（含全局与各应用），用于维度 ID → 编码映射。 */
    List<DataDimension> listAll();

    /** 按主键查询。 */
    Optional<DataDimension> findById(String id);

    /** 按编码查询：{@code appCode} 为空查全局维度，否则查该应用维度。 */
    Optional<DataDimension> findByCode(String appCode, String code);

    /** 按编码查询（全局优先，不限应用），用于人员维度授予等无应用上下文的场景。 */
    Optional<DataDimension> findByCodeAnywhere(String code);

    /** 新增维度。 */
    void insert(DataDimension dimension);

    /** 改名称、是否层级与值来源（编码不可改）。 */
    void update(DataDimension dimension);
}
