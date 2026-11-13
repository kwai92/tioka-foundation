package com.foundation.org.api;

import com.foundation.org.api.dto.OrgUnitView;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 组织单元只读契约（F-03 §5）：其他模块（BAS-05 权限、BAS-06 数据范围）只依赖本接口。
 *
 * <p>写操作在 Foundation 管理台与本域应用服务，不经本接口对外暴露。
 */
public interface OrgApi {

    /** 按 ID 取组织单元。 */
    Optional<OrgUnitView> findById(String orgUnitId);

    /** 按编码取组织单元。 */
    Optional<OrgUnitView> findByCode(String code);

    /** 直接下级（不含自身），按 {@code sort_no} 升序；{@code orgUnitId} 为空表示根节点。 */
    List<OrgUnitView> childrenOf(String orgUnitId);

    /**
     * 子树 ID 集合（含自身），按物化路径前缀查询，供「本部门及以下」免递归（F-03 §2.1）。
     *
     * <p>不存在的组织单元返回空集合。
     */
    Set<String> descendantIds(String orgUnitId);
}
