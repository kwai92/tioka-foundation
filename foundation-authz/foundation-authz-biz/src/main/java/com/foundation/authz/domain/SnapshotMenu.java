package com.foundation.authz.domain;

import java.util.List;
import java.util.Objects;

/**
 * 快照中的可见菜单节点（F-06 BAS-07 §粒度与内容）：目录/菜单/按钮。
 *
 * @param code     菜单编码
 * @param type     {@link MenuType} 名称
 * @param children 下级节点
 */
public record SnapshotMenu(String code, String type, List<SnapshotMenu> children) {

    public SnapshotMenu {
        Objects.requireNonNull(code, "code");
        children = children == null ? List.of() : List.copyOf(children);
    }
}
