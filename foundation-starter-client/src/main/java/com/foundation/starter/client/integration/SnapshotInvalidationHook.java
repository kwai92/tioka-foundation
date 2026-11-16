package com.foundation.starter.client.integration;

/**
 * 授权快照失效钩子：平台侧留给派生应用注册的可选 SPI。
 *
 * <p>收到 {@code fdn.authz.changed} 一类事件时，应用应调用本钩子失效本地缓存；
 * 平台不规定缓存实现，也不依赖 {@code foundation-starter-security}。
 * 未注册时自检报告记为 WARN（P1 运行平面待接），不影响「接入是否成立」的判定。
 */
@FunctionalInterface
public interface SnapshotInvalidationHook {

    /** 使指定人员的本地授权快照失效；{@code null} 表示全量失效。 */
    void invalidate(String personId);
}
