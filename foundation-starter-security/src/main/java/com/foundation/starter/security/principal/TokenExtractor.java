package com.foundation.starter.security.principal;

import java.util.Optional;

/**
 * 从请求对象中提取令牌字符串（F-07 §2）。
 *
 * <p>抽出为独立接口，使 {@link JwtPrincipalResolver} 与具体 web 技术栈解耦（可测）。
 */
@FunctionalInterface
public interface TokenExtractor {

    /** 提取令牌原文；无令牌返回空。 */
    Optional<String> extract(Object request);
}
