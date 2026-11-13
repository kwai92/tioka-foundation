package com.foundation.org.api.dto;

import java.util.List;

/**
 * 组织树节点（F-03 §5 {@code GET /api/fdn/org/tree}）。
 *
 * @param unit          节点自身的组织单元视图
 * @param positionCount 节点下岗位数
 * @param childCount    直接下级数
 * @param children      下级节点
 */
public record OrgUnitTreeNode(OrgUnitView unit,
                              int positionCount,
                              int childCount,
                              List<OrgUnitTreeNode> children) {
}
