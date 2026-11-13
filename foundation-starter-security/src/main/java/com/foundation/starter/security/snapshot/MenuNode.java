package com.foundation.starter.security.snapshot;

import java.util.List;

/**
 * 可见菜单节点（F-07 §4；与 {@code AuthzSnapshotView.MenuNode} 同构）。
 *
 * @param code     菜单编码
 * @param type     {@code DIR} / {@code MENU} / {@code BUTTON}
 * @param children 下级节点
 */
public record MenuNode(String code, String type, List<MenuNode> children) {

    public MenuNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
