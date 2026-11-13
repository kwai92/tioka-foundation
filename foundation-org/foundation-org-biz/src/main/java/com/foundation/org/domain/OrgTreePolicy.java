package com.foundation.org.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;

import java.util.Arrays;
import java.util.Set;

/**
 * 组织树规则（F-03 §7.3）：树深 ≤ 上限、防环、停用前置校验、物化路径维护。
 *
 * <p>纯规则，不依赖 Spring 与持久化；应用服务在事务内调用。
 */
public class OrgTreePolicy {

    /** 物化路径分隔符。 */
    public static final String SEPARATOR = "/";

    private final int maxDepth;

    public OrgTreePolicy(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        this.maxDepth = maxDepth;
    }

    /** 最大树深（{@code foundation.org.max-tree-depth}）。 */
    public int maxDepth() {
        return maxDepth;
    }

    /** 根节点路径：{@code /org_x}。 */
    public String rootPath(String orgUnitId) {
        return SEPARATOR + orgUnitId;
    }

    /** 子节点路径：{@code 父路径 + / + 本节点 ID}。 */
    public String childPath(String parentPath, String orgUnitId) {
        return parentPath + SEPARATOR + orgUnitId;
    }

    /** 路径深度（段数）。 */
    public int depthOfPath(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        return (int) Arrays.stream(path.split(SEPARATOR)).filter(segment -> !segment.isBlank()).count();
    }

    /** 由上级路径推导本级深度：无上级（根）为 1。 */
    public int depthOfChild(String parentPath) {
        return parentPath == null || parentPath.isBlank() ? 1 : depthOfPath(parentPath) + 1;
    }

    /** 树深不超过上限，否则拒绝（第 11 层拒绝）。 */
    public void assertDepthWithinLimit(int depth) {
        if (depth < 1 || depth > maxDepth) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "组织树深度超限：depth=" + depth + "，上限 " + maxDepth);
        }
    }

    /**
     * 移动防环：新上级不得是自身，也不得落在自身子树内。
     *
     * @param movingId         被移动节点 ID
     * @param newParentId      目标上级 ID
     * @param movingSubtreeIds 被移动节点的子树 ID 集合（含自身）；为空按「仅自身」处理
     */
    public void assertNoCycle(String movingId, String newParentId, Set<String> movingSubtreeIds) {
        if (newParentId == null || newParentId.isBlank()) {
            return;
        }
        if (newParentId.equals(movingId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "组织单元不能以自身为上级");
        }
        if (movingSubtreeIds != null && movingSubtreeIds.contains(newParentId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "移动会形成环：目标上级在当前组织单元的子树内");
        }
    }

    /**
     * 停用前置：下级与在岗人员必须已迁出（F-03 §2.1）。
     *
     * @param hasChildren           是否存在下级组织单元
     * @param activeEmploymentCount 未结束任职数（在岗人员）
     */
    public void assertDeactivatable(boolean hasChildren, long activeEmploymentCount) {
        if (hasChildren) {
            throw new BizException(ErrorCode.CONFLICT, "存在下级组织单元，停用前要求已迁出");
        }
        if (activeEmploymentCount > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "存在在岗人员 " + activeEmploymentCount + " 人，停用前要求已迁出");
        }
    }

    /** 子树路径改写：把 {@code oldPrefix} 前缀替换为 {@code newPrefix}。 */
    public String rewritePath(String path, String oldPrefix, String newPrefix) {
        if (path == null || !path.startsWith(oldPrefix)) {
            throw new IllegalArgumentException("路径不在被移动子树内：" + path);
        }
        return newPrefix + path.substring(oldPrefix.length());
    }
}
