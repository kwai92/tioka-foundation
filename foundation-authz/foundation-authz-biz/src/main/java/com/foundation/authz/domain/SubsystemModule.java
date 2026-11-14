package com.foundation.authz.domain;

/**
 * 模块（F-05 BAS-27 §目录）：子系统下的第一层。
 *
 * @param id          模块 ID（{@code mod_{ulid}}）
 * @param subsystemId 所属子系统 ID
 * @param code        编码（子系统内唯一）
 * @param name        名称
 * @param sort        排序
 * @param status      状态：{@code ACTIVE} / {@code OFFLINE}
 */
public record SubsystemModule(String id,
                              String subsystemId,
                              String code,
                              String name,
                              int sort,
                              String status) {

    /** 是否启用。 */
    public boolean active() {
        return "ACTIVE".equals(status);
    }

    /** 副本：置为下线。 */
    public SubsystemModule offline() {
        return new SubsystemModule(id, subsystemId, code, name, sort, "OFFLINE");
    }
}
