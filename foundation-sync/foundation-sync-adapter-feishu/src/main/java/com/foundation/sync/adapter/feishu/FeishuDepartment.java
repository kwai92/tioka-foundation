package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.SyncObjectType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书部门（F-08 BAS-12）。
 *
 * @param openDepartmentId       部门 open_id（幂等键）
 * @param parentOpenDepartmentId 上级部门 open_id，可空
 * @param name                   部门名称
 * @param order                  排序值
 * @param leaderOpenIds          部门负责人 open_id 列表
 */
public record FeishuDepartment(String openDepartmentId,
                               String parentOpenDepartmentId,
                               String name,
                               int order,
                               List<String> leaderOpenIds) {

    public FeishuDepartment {
        leaderOpenIds = leaderOpenIds == null ? List.of() : List.copyOf(leaderOpenIds);
    }

    /** 便利构造。 */
    public static FeishuDepartment of(String openDepartmentId, String parentOpenDepartmentId, String name,
                                      int order, List<String> leaderOpenIds) {
        return new FeishuDepartment(openDepartmentId, parentOpenDepartmentId, name, order, leaderOpenIds);
    }

    /** 映射为来源记录（ORG_UNIT）。 */
    public ExternalRecord toExternalRecord() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("department_id", openDepartmentId);
        fields.put("parent_id", parentOpenDepartmentId);
        fields.put("name", name);
        fields.put("order", order);
        if (!leaderOpenIds.isEmpty()) {
            fields.put("leader_person_id", leaderOpenIds.get(0));
        }
        return new ExternalRecord(SyncObjectType.ORG_UNIT, openDepartmentId, fields,
                parentOpenDepartmentId, false, null);
    }
}
