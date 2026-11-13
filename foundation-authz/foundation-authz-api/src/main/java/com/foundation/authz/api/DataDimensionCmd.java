package com.foundation.authz.api;

/**
 * 新建数据维度入参（F-06 BAS-06 §接口）。
 *
 * @param appCode      所属应用编码，可空（空 = 全局维度）
 * @param code         维度编码，创建后不可改
 * @param name         名称
 * @param valueSource  {@code DICT} / {@code ORG_TREE} / {@code APP_REPORTED}
 * @param hierarchical 是否层级维度
 */
public record DataDimensionCmd(String appCode,
                               String code,
                               String name,
                               String valueSource,
                               boolean hierarchical) {
}
