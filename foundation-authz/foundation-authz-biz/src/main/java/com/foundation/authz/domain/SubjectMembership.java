package com.foundation.authz.domain;

import java.util.Set;

/**
 * 人员在组织侧的归属（F-05 BAS-05 §有效角色与互斥）。
 *
 * <p>组织单元与岗位数据归组织域（BAS-06），本域只通过该端口读取，避免跨模块直连；
 * BAS-06 落地前由 {@code NoopSubjectMembership} 占位（返回空集合）。
 */
public interface SubjectMembership {

    /** 人员直接挂靠的组织单元 ID 集合。 */
    Set<String> orgUnitsOf(String personId);

    /** 人员当前岗位 ID 集合。 */
    Set<String> postsOf(String personId);

    /** 组织单元的子树（含自身）ID 集合。 */
    Set<String> descendantsOf(String orgUnitId);

    /**
     * 指定组织单元下的任职人员（供受影响人员判定，F-06 BAS-07 §快照计算）。
     *
     * <p>BAS-02 之前的占位实现返回空集合。
     */
    default Set<String> personsInOrgUnit(String orgUnitId) {
        return Set.of();
    }

    /** 指定岗位下的任职人员；占位实现返回空集合。 */
    default Set<String> personsInPost(String postId) {
        return Set.of();
    }
}
