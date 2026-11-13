package com.foundation.org.api.dto;

/**
 * 岗位写入（F-03 §5 {@code GET/POST /api/fdn/positions}、{@code PUT/{id}}）。
 *
 * @param code      编码（唯一）
 * @param name      名称
 * @param orgUnitId 所属组织单元 ID，空表示全局岗位
 * @param sortNo    排序，空取 0
 */
public record PositionCmd(String code,
                          String name,
                          String orgUnitId,
                          Integer sortNo) {
}
