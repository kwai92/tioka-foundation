package com.foundation.authz.domain;

import java.util.List;

/**
 * 快照中的人员属性（F-06 BAS-07 §粒度与内容）：主组织单元、岗位、职级。
 *
 * <p>同时作为 GOV-07 Cedar 与 ADR-040 CEL 的属性来源。BAS-02 组织与人员落地前，
 * 经 {@link SubjectMembership} 端口读到的主组织单元与岗位可能为空、职级为空。
 *
 * @param primaryOrgUnit 主组织单元，可空
 * @param positions      岗位集合
 * @param grade          职级，可空
 */
public record SnapshotPerson(String primaryOrgUnit, List<String> positions, String grade) {

    public SnapshotPerson {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }
}
