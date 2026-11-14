package com.foundation.authz.api;

import java.util.List;

/**
 * 目录节点视图（F-05 BAS-27 §目录）。
 *
 * <p>三层结构：{@code MODULE}（模块）→ {@code FUNCTION}（功能）→ {@code OPERATION}（操作）。
 *
 * @param level    层级：{@code MODULE} / {@code FUNCTION} / {@code OPERATION}
 * @param code     编码
 * @param name     名称
 * @param menuId   关联菜单 ID，可空
 * @param enabled  是否启用（受功能开关影响）
 * @param children 下级节点
 */
public record DirectoryNodeView(String level,
                                String code,
                                String name,
                                String menuId,
                                boolean enabled,
                                List<DirectoryNodeView> children) {
}
