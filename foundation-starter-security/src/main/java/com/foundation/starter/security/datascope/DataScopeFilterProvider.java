package com.foundation.starter.security.datascope;

import com.foundation.starter.security.snapshot.AuthzSnapshot;

/**
 * 数据范围条件生成器（F-07 §5）。
 *
 * <p>语义（S15 §11.4、F-06）：部门五档 {@code ALL} / {@code CUSTOM} / {@code DEPT} / {@code DEPT_AND_SUB} /
 * {@code SELF}；跨角色取并集、同一角色内部门范围与各维度取交集（这些归约在 Foundation 侧完成，
 * 快照里已是最终取值集合）；{@code all=true} 时生成 {@code 1=1} 且不追加维度条件。
 * <b>默认拒绝</b>：快照缺失或取不到 → 抛 {@code FORBIDDEN}。
 */
public interface DataScopeFilterProvider {

    /** 按快照生成过滤条件。 */
    DataScopeFilter filter(String departmentColumn, String dimension,
                           String dimensionColumn, AuthzSnapshot snapshot);
}
