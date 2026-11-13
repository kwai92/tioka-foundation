package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.PersonDimensionGrant;

import java.util.List;

/** 人员维度授予仓储（F-06 BAS-06 §数据模型 {@code fdn_person_dimension}）。 */
public interface PersonDimensionRepository {

    /** 人员的全部维度授予。 */
    List<PersonDimensionGrant> listByPerson(String personId);

    /** 人员在某维度上的授予（用于手工授予保护判定）。 */
    List<PersonDimensionGrant> listByPersonAndDimension(String personId, String dimensionId);

    /** 新增授予行。 */
    void insert(PersonDimensionGrant grant);

    /** 撤销人员在某维度上的授予（不动角色授予）。 */
    void deleteByPersonAndDimension(String personId, String dimensionId);
}
