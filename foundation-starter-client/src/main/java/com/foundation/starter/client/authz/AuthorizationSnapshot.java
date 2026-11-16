package com.foundation.starter.client.authz;

import java.util.Set;

/**
 * 平台侧授权快照视图（P1 运行平面）：派生应用接入中台所需的最小字段集。
 *
 * <p>与服务端 {@code com.foundation.authz.api.AuthzSnapshotView} 同源，但只保留判定链路
 * 需要的字段：身份标识、应用编码、单调版本号与中台原始权限项。角色、菜单、部门范围等
 * 由标准路径（{@code foundation-starter-security}）消费，轻量路径刻意不解析，
 * 避免平台替业务方解释授权语义。
 *
 * <p><b>version 是缓存与失效的核心</b>：按 {@code (personId, appCode)} 单调递增。
 * 客户端不得接受版本回退的快照，也不得在回源失败时返回陈旧快照（ADR-051 第 5 条、
 * 36 号规划 §6「身份与授权类 fail-closed」）。
 *
 * @param appCode     应用编码
 * @param personId    人员 ID
 * @param version     快照版本号（非负整数，单调递增）
 * @param permissions 中台原始权限项；空集合表示中台明确授予零个权限
 */
public record AuthorizationSnapshot(String appCode, String personId, long version,
                                    Set<String> permissions) {

    public AuthorizationSnapshot {
        if (appCode == null || appCode.isBlank()) {
            throw new IllegalArgumentException("appCode is required");
        }
        if (personId == null || personId.isBlank()) {
            throw new IllegalArgumentException("personId is required");
        }
        if (version < 0) {
            throw new IllegalArgumentException("snapshot version must be non-negative");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("permissions is required (empty means zero grants)");
        }
        permissions = Set.copyOf(permissions);
    }
}
