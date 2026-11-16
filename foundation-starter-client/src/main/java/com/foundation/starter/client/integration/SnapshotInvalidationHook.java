package com.foundation.starter.client.integration;

/**
 * 授权快照失效钩子：平台侧留给派生应用注册的可选 SPI。
 *
 * <p>收到 {@code fdn.authz.changed} 一类事件时，应用应调用本钩子失效本地缓存；
 * 平台不规定缓存实现，也不依赖 {@code foundation-starter-security}。
 *
 * <p>P1 起平台在接入模式自动把本钩子指向 {@code AuthorizationSnapshotCache}，
 * 并提供 {@code AuthorizationSnapshotInvalidationListener} 把事件报文转成失效调用；
 * 应用自行注册 Bean 可完全接管。未注册时自检报告记为 WARN（退化依赖 TTL 兜底），
 * 不影响「接入是否成立」的判定。
 */
@FunctionalInterface
public interface SnapshotInvalidationHook {

    /** 使指定人员的本地授权快照失效；{@code null} 表示全量失效。 */
    void invalidate(String personId);
}
