package com.foundation.starter.security.principal;

import java.util.Optional;

/**
 * 令牌来源抽象（F-07 §2）：把未认证请求解析为 {@link Principal}。
 *
 * <p>两种令牌来源并存，由本抽象隔离：
 * <ul>
 *   <li>{@code jwt}：{@link JwtPrincipalResolver}，验签、验 {@code aud}、提取身份声明；</li>
 *   <li>{@code opaque}：应用自定义实现（Tioka 的 {@code tka_} 令牌，BAS-20 落地）。</li>
 * </ul>
 * 实现必须**不抛异常**：无令牌 / 无效一律返回 {@link Optional#empty()}。
 */
public interface PrincipalResolver {

    /** 从未认证请求中解析身份；无令牌或无效返回 {@link Optional#empty()}，不抛异常。 */
    Optional<Principal> resolve(Object request);
}
