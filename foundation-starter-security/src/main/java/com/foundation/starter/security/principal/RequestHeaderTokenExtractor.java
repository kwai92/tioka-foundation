package com.foundation.starter.security.principal;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/** 从 {@code Authorization: Bearer <token>} 头提取令牌（F-07 §2）。 */
public class RequestHeaderTokenExtractor implements TokenExtractor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Override
    public Optional<String> extract(Object request) {
        if (!(request instanceof HttpServletRequest servletRequest)) {
            return Optional.empty();
        }
        String value = servletRequest.getHeader(HEADER);
        if (value == null || !value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String token = value.substring(PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }
}
