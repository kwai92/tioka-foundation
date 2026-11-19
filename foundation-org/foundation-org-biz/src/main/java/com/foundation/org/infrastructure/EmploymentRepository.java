package com.foundation.org.infrastructure;

import com.foundation.org.domain.Employment;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 任职仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface EmploymentRepository {

    Optional<Employment> findById(String id);

    /** 人员的全部任职（含历史），按开始日期升序。 */
    List<Employment> byPerson(String personId);

    /** 人员未结束的任职。 */
    List<Employment> openByPerson(String personId);

    /** 人员未结束的主任职。 */
    Optional<Employment> openPrimary(String personId);

    /** 组织单元下未结束的任职。 */
    List<Employment> openByOrgUnit(String orgUnitId);

    /** 一组组织单元下未结束任职的人员 ID 集合。 */
    Set<String> openPersonIdsInOrgUnits(Collection<String> orgUnitIds);

    /**
     * 某岗位下未结束任职的人员 ID 集合（供授权域计算「岗位授权受影响人员」，F-06 BAS-07）。
     *
     * <p>{@code position_id} 列可空（任职可不挂岗位），故本方法只返回**确实挂了该岗位**的未结束任职人员；
     * 入参为空或空白时返回空集合，不产生 {@code position_id IS NULL} 这类会捞出全部无岗位任职的查询。
     */
    Set<String> openPersonIdsInPosition(String positionId);

    /** 组织单元下未结束任职数（停用前置校验用）。 */
    long countOpenByOrgUnit(String orgUnitId);

    void insert(Employment employment);

    void update(Employment employment);

    /** 结束任职（保留历史，不物理删除，48-CS §4）。 */
    void close(String id, LocalDate endDate);

    void deleteById(String id);
}
