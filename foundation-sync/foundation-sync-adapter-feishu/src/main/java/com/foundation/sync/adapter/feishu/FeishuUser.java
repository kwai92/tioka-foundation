package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.SyncObjectType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞书用户（F-08 BAS-12）。
 *
 * @param openId       用户 open_id（幂等键）
 * @param unionId      用户 union_id
 * @param userId       用户 user_id
 * @param name         姓名
 * @param email        邮箱
 * @param mobile       手机号
 * @param employeeNo   工号
 * @param departmentIds 所属部门 open_id 列表
 * @param active       是否在用（未删除、未冻结）
 * @param updatedAt    来源侧最后更新时间，可空
 */
public record FeishuUser(String openId,
                         String unionId,
                         String userId,
                         String name,
                         String email,
                         String mobile,
                         String employeeNo,
                         List<String> departmentIds,
                         boolean active,
                         Instant updatedAt) {

    public FeishuUser {
        departmentIds = departmentIds == null ? List.of() : List.copyOf(departmentIds);
    }

    /** 便利构造。 */
    public static FeishuUser of(String openId, String unionId, String userId, String name,
                                String email, String mobile, String employeeNo,
                                List<String> departmentIds, boolean active, Instant updatedAt) {
        return new FeishuUser(openId, unionId, userId, name, email, mobile, employeeNo,
                departmentIds, active, updatedAt);
    }

    /** 映射为来源记录（PERSON）。 */
    public ExternalRecord toExternalRecord() {
        Map<String, Object> fields = new LinkedHashMap<>();
        putIfPresent(fields, "open_id", openId);
        putIfPresent(fields, "union_id", unionId);
        putIfPresent(fields, "user_id", userId);
        putIfPresent(fields, "name", name);
        putIfPresent(fields, "email", email);
        putIfPresent(fields, "mobile", mobile);
        putIfPresent(fields, "emp_no", employeeNo);
        return new ExternalRecord(SyncObjectType.PERSON, openId, fields, null, !active, updatedAt);
    }

    private static void putIfPresent(Map<String, Object> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }
}
