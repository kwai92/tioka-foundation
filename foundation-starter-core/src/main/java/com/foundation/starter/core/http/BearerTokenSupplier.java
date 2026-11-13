package com.foundation.starter.core.http;

/**
 * 出站调用令牌供应（F-07 §11）：为 starter 调 Foundation 只读接口提供 Bearer 令牌。
 *
 * <p>契约放在 {@code core}，使 {@code security}（取快照）与 {@code client}（取字典 / 组织）
 * 都能复用而**不互相依赖**（F-07 §12：starter 之间只依赖 core）。实现由
 * {@code foundation-starter-client} 的 {@code ServiceAccountTokenProvider} 提供；
 * 无实现时返回 {@code null}，出站请求不带 Authorization。
 */
@FunctionalInterface
public interface BearerTokenSupplier {

    /** 当前可用的出站令牌；不可用时返回 {@code null}（出站请求不带 Authorization）。 */
    String bearerToken();
}
