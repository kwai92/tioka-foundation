package com.foundation.authz.api;

/**
 * 修改数据维度入参（F-06 BAS-06 §接口）；{@code code} 与 {@code appCode} 不可改。
 *
 * <p>{@code valueSource} 变更时清空该维度既有值。
 *
 * @param name         名称
 * @param hierarchical 是否层级维度
 * @param valueSource  值来源
 */
public record DataDimensionUpdateCmd(String name, boolean hierarchical, String valueSource) {
}
