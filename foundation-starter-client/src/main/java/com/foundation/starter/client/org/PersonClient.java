package com.foundation.starter.client.org;

import java.util.List;
import java.util.Optional;

/**
 * 人员查询（F-07 §8）：按工号 / ID 查人员，带本地缓存。
 *
 * <p>只读：人员的增删改在 Foundation 管理台，starter 不提供写接口；{@code fdn.person.disabled}
 * 的**离职联动**由应用侧监听处理（F-00 §5 约定 7）。
 */
public interface PersonClient {

    /** 按工号查人员。 */
    Optional<Person> byEmployeeNo(String employeeNo);

    /** 按人员 ID 查人员。 */
    Optional<Person> byId(String personId);

    /** 组织下人员；{@code includeSub} 是否含下级组织。 */
    List<Person> byOrgUnit(String orgCode, boolean includeSub);
}
