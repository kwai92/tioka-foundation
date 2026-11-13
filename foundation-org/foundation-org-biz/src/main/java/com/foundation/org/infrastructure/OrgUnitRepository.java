package com.foundation.org.infrastructure;

import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.domain.OrgUnit;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 组织单元仓储（F-03 §7.3，MyBatis-Plus）。 */
public interface OrgUnitRepository {

    Optional<OrgUnit> findById(String id);

    Optional<OrgUnit> findByCode(String code);

    Optional<OrgUnit> findBySourceExternalId(String source, String externalId);

    boolean existsByCode(String code);

    /** 直接下级，按 {@code sort_no} 升序；{@code parentId} 为空表示根节点。 */
    List<OrgUnit> childrenOf(String parentId);

    /** 子树（含自身），按物化路径前缀查询。 */
    List<OrgUnit> subtreeOf(String orgUnitId);

    /** 全部组织单元，按物化路径升序（组织树整树装配用）。 */
    List<OrgUnit> findAll();

    /** 子树 ID 集合（含自身）。 */
    Set<String> descendantIds(String orgUnitId);

    List<OrgUnit> search(String q, OrgUnitType type, OrgStatus status, int page, int size);

    long count(String q, OrgUnitType type, OrgStatus status);

    void insert(OrgUnit orgUnit);

    void update(OrgUnit orgUnit);

    /**
     * 移动子树后级联重写后代节点的 {@code path} 与 {@code depth}。
     *
     * @param oldPrefix 原子树前缀（{@code oldPath + "/"}）
     * @param newPrefix 新子树前缀（{@code newPath + "/"}）
     * @param depthDelta 深度增量
     * @return 受影响行数
     */
    int rewriteSubtree(String oldPrefix, String newPrefix, int depthDelta);
}
