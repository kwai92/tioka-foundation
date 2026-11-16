package com.foundation.starter.client.authz;

import java.util.Set;

/**
 * 中台授权快照的只读客户端（F-06 BAS-07、F-07 §4）。
 *
 * <p>只暴露「按人员取中台原始权限项」这一能力，供派生应用把
 * {@code /api/fdn/apps/{appCode}/persons/{personId}/snapshot} 接入自己的授权判定端口。
 * 与 {@code foundation-starter-security} 的 {@code AuthzSnapshotClient} 职责不同：本接口不缓存、
 * 不降级、不装配服务端安全过滤器，只是复用底座已有的出站通道（版本头、服务账号令牌、
 * 重试、熔断、trace 透传），适合只想接中台授权的轻量派生应用。
 *
 * <p><b>契约</b>：返回空集合表示「已成功查询且确实无权限」；取不到数据必须返回 {@code null}
 * 或抛异常，调用方一律 fail-closed，绝不把空数据当成「无权限」以外的含义。
 */
@FunctionalInterface
public interface AuthorizationSnapshotClient {

    /** 取指定人员在当前应用下的中台原始权限项；不可用返回 {@code null} 或抛异常。 */
    Set<String> permissionsFor(String personId);
}
