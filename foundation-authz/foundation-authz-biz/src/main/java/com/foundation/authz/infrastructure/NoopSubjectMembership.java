package com.foundation.authz.infrastructure;

import com.foundation.authz.domain.SubjectMembership;

import java.util.Set;

/**
 * 组织归属端口的占位实现（F-05 BAS-05 §有效角色与互斥）。
 *
 * <p>组织单元与岗位数据归组织域（BAS-06）；在该模块落地并装配同名 {@link SubjectMembership}
 * Bean 之前，组织单元与岗位两条来源均为空，有效角色只由「直接授予」构成。
 *
 * <p>岗位的所属组织单元（{@code orgUnitsOfPost}）同样为空——直接沿用接口上的 default 实现，
 * 不重复声明。其后果是：分级管理员越权拦截（{@code OrgAdminScopeGuard}）对 {@code POST} 型授权
 * 解析不出目标组织单元，按 fail-closed 拒绝；即占位实现下 {@code POST} 型授权只能由未配范围的
 * 全局管理员执行。
 */
public class NoopSubjectMembership implements SubjectMembership {

    @Override
    public Set<String> orgUnitsOf(String personId) {
        return Set.of();
    }

    @Override
    public Set<String> postsOf(String personId) {
        return Set.of();
    }

    @Override
    public Set<String> descendantsOf(String orgUnitId) {
        return Set.of(orgUnitId);
    }
}
