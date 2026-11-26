package com.foundation.sync.adapter.feishu;

import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 飞书根部门（无上级部门）映射回归（F-08 BAS-12）。
 *
 * <p>缺陷背景：{@code HttpFeishuApiClient.text()} 对缺失/空白的 JSON 字段返回 {@code null}，
 * 而 {@code FeishuDepartment.toExternalRecord()} <b>无条件</b>执行
 * {@code fields.put("parent_id", parentOpenDepartmentId)}（对比 {@code FeishuUser.toRecord}
 * 全程用 {@code putIfPresent} 跳过 null）。飞书<b>根部门没有 {@code parent_department_id}</b>，
 * 于是根部门的 fields 含 null 值；旧版 {@code ExternalRecord} 构造器用 {@code Map.copyOf}
 * （拒绝 null 值）→ 抛 NPE → 被 {@code SyncEngine.execute} 吞成任务 FAILED 且不打栈，
 * 表现为「飞书同步莫名失败」。
 *
 * <p>修复在 {@code ExternalRecord}（改用容忍 null 的不可变 Map），本测试锁定该路径：
 * 用与生产完全相同的取值形态（parent/name 为 null）构造 {@code FeishuDepartment} 并映射，
 * 断言不抛异常、null 值原样保留、键集完整。
 */
class FeishuDepartmentNullFieldTest {

    @Test
    @DisplayName("根部门（parent_department_id 缺失→null）映射不得抛 NPE，null 值原样保留")
    void rootDepartmentWithNullParentMapsWithoutNpe() {
        // 与 HttpFeishuApiClient.parseDepartment 的真实取值一致：text() 对缺失字段返回 null。
        // name 为 null 也一并覆盖（text() 对空白 name 同样返回 null，无条件 put 同样带 null 值）。
        FeishuDepartment root = FeishuDepartment.of("od-root", null, null, 0, List.of());

        ExternalRecord record = root.toExternalRecord();

        assertThat(record.objectType()).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(record.externalId()).isEqualTo("od-root");
        assertThat(record.fields())
                .as("键集必须完整（含值为 null 的键），FieldMapper 的「键缺失 vs 值为 null」语义依赖它")
                .containsKeys("department_id", "parent_id", "name", "order");
        assertThat(record.fields().get("parent_id"))
                .as("根部门的 parent_id 应为 null 而非抛 NPE")
                .isNull();
        assertThat(record.fields().get("name")).isNull();
        assertThat(record.fields().get("department_id")).isEqualTo("od-root");
        // parentExternalId 属性同样透传 null（组织单元移动判定依赖它）
        assertThat(record.parentExternalId()).isNull();
    }

    @Test
    @DisplayName("ExternalRecord 保持不可变且容忍任意 null 值字段")
    void externalRecordFieldsAreImmutableAndNullTolerant() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("open_id", "ou-1");
        fields.put("email", null);
        fields.put("mobile", null);
        ExternalRecord record = ExternalRecord.of(SyncObjectType.PERSON, "ou-1", fields);

        assertThat(record.fields()).containsEntry("open_id", "ou-1");
        assertThat(record.fields().get("email")).isNull();

        // 防御性复制：构造后修改原 Map 不影响记录
        fields.put("injected", "x");
        assertThat(record.fields()).doesNotContainKey("injected");

        // 不可变：直接改记录持有的 Map 应失败
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> record.fields().put("y", "z"),
                "fields 必须不可变，否则来源侧原始取值可被下游篡改");
    }

    @Test
    @DisplayName("withFields 回填映射结果同样容忍 null 值")
    void withFieldsToleratesNullValues() {
        ExternalRecord source = ExternalRecord.of(SyncObjectType.PERSON, "ou-1",
                Map.of("email", "a@b.c"));
        Map<String, Object> mapped = new LinkedHashMap<>();
        mapped.put("email", "a@b.c");
        mapped.put("mobile", null); // FieldMapper 对无默认值的缺失字段可能产出 null

        ExternalRecord remapped = source.withFields(mapped);

        assertThat(remapped.fields()).containsEntry("email", "a@b.c");
        assertThat(remapped.fields().get("mobile")).isNull();
        assertThat(remapped.deleted()).isFalse();
    }
}
