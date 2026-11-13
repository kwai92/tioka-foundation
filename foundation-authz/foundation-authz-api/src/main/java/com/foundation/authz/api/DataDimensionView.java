package com.foundation.authz.api;

/**
 * 数据维度视图（F-06 BAS-06 §数据模型）。
 *
 * @param id           维度 ID
 * @param appCode      所属应用编码；{@code null} 表示全局维度
 * @param code         维度编码
 * @param name         名称
 * @param valueSource  {@code DICT} / {@code ORG_TREE} / {@code APP_REPORTED}
 * @param hierarchical 是否层级维度
 */
public record DataDimensionView(String id,
                                String appCode,
                                String code,
                                String name,
                                String valueSource,
                                boolean hierarchical) {
}
