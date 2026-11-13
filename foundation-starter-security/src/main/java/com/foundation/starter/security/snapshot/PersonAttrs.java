package com.foundation.starter.security.snapshot;

import java.util.Set;

/**
 * 人员属性（F-07 §4；同时作为 GOV-07 Cedar 与 ADR-040 CEL 的属性来源）。
 *
 * @param primaryOrgUnit 主组织单元，可空
 * @param positions      岗位集合
 * @param grade          职级，可空
 */
public record PersonAttrs(String primaryOrgUnit, Set<String> positions, String grade) {

    public PersonAttrs {
        positions = positions == null ? Set.of() : Set.copyOf(positions);
    }
}
