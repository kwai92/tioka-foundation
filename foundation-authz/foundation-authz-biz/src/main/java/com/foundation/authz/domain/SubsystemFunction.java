package com.foundation.authz.domain;

/**
 * 功能（F-05 BAS-27 §目录）：模块下的第二层，可关联菜单。
 *
 * @param id       功能 ID（{@code fun_{ulid}}）
 * @param moduleId 所属模块 ID
 * @param code     编码（模块内唯一）
 * @param name     名称
 * @param menuId   关联菜单 ID，可空
 * @param sort     排序
 * @param status   状态：{@code ACTIVE} / {@code OFFLINE}
 */
public record SubsystemFunction(String id,
                                String moduleId,
                                String code,
                                String name,
                                String menuId,
                                int sort,
                                String status) {

    /** 是否启用。 */
    public boolean active() {
        return "ACTIVE".equals(status);
    }

    /** 副本：置为下线。 */
    public SubsystemFunction offline() {
        return new SubsystemFunction(id, moduleId, code, name, menuId, sort, "OFFLINE");
    }

    /** 副本：关联菜单。 */
    public SubsystemFunction withMenuId(String newMenuId) {
        return new SubsystemFunction(id, moduleId, code, name, newMenuId, sort, status);
    }
}
