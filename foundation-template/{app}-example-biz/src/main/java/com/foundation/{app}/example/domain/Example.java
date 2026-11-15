package com.foundation.{app}.example.domain;

/**
 * 示例领域对象（BAS-25 §分层：{@code domain} 不依赖框架）。
 *
 * <p>禁止 {@code tenantId} 字段（ADR-029 第 60 条：单企业独立部署）。
 */
public record Example(String id, String name) {
}
