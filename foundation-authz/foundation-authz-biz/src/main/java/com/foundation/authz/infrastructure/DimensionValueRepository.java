package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.DimensionValue;

import java.util.List;
import java.util.Optional;

/** 维度值仓储（F-06 BAS-06 §数据模型 {@code fdn_dimension_value}）；停用置 {@code active = false} 且保留行。 */
public interface DimensionValueRepository {

    /** 维度下的全部值（含已停用）。 */
    List<DimensionValue> listByDimension(String dimensionId);

    /** 按编码查询。 */
    Optional<DimensionValue> findByCode(String dimensionId, String code);

    /** 新增值。 */
    void insert(DimensionValue value);

    /** 改名称 / 上级 / 生效状态。 */
    void update(DimensionValue value);

    /** 停用值（保留行）。 */
    void deactivate(String dimensionId, String code);

    /** 清空维度下的值（值来源变更时）。 */
    void deleteByDimension(String dimensionId);
}
