package com.foundation.starter.client.org;

/**
 * 组织单元（F-07 §8）。
 *
 * @param id       组织单元 ID
 * @param code     组织单元编码
 * @param name     名称
 * @param parentId 上级组织单元 ID，可空
 * @param sort     排序
 */
public record OrgUnit(String id, String code, String name, String parentId, int sort) {
}
