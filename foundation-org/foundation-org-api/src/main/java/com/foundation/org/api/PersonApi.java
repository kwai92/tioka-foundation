package com.foundation.org.api;

import com.foundation.org.api.dto.PersonView;

import java.util.Optional;
import java.util.Set;

/**
 * 人员只读契约（F-03 §5）：其他模块（BAS-05 权限、BAS-06 数据范围）只依赖本接口。
 *
 * <p>返回的 {@link PersonView} 为明文视图（手机 / 邮箱未脱敏）：本接口是模块间内部边界，
 * 脱敏只作用于管理台 HTTP 出参（F-03 §4.2）。
 */
public interface PersonApi {

    /** 按 ID 取人员。 */
    Optional<PersonView> findById(String personId);

    /** 按工号取人员。 */
    Optional<PersonView> findByEmpNo(String empNo);

    /**
     * 组织单元下的任职人员 ID 集合。
     *
     * @param orgUnitId       组织单元 ID
     * @param includeSubUnits 是否含下级组织单元（按物化路径展开）
     */
    Set<String> personsInOrgUnit(String orgUnitId, boolean includeSubUnits);

    /** 人员当前主任职所在组织单元 ID，无主任职时为空。 */
    Optional<String> primaryOrgUnitId(String personId);

    /** 人员直属上级人员 ID，未维护时为空。 */
    Optional<String> managerId(String personId);
}
