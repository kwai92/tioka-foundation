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
     * 岗位所属的组织单元 ID 集合（F-05 BAS-27 §分级授权的越权判定用）。
     *
     * <p>返回集合而非单值：岗位模型上归属一个组织单元（{@code fdn_position.org_unit_id}），
     * 但该列为空时岗位不归属任何单元，此时应返回空集以便调用方按「解析不出组织单元」处理。
     * 占位实现返回空集合。
     */
    default Set<String> orgUnitsOfPost(String postId) {
        return Set.of();
    }

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
