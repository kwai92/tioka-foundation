package com.foundation.starter.data;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DO 基类族约定（F-02 BAS-24 §foundation-starter-data）：审计列集合、时间列类型、无租户字段（ADR-029 第 60 条）。
 */
class RecordDoContractTest {

    private static final List<String> CREATE_ONLY = List.of("createAt", "createBy");
    private static final List<String> MUTABLE_AUDIT =
            List.of("createAt", "updateAt", "createBy", "updateBy");

    @Test
    void baseDoCarriesFourAuditColumnsAndLogicalDelete() {
        assertThat(fieldNames(BaseDO.class))
                .containsExactlyInAnyOrder("createAt", "updateAt", "createBy", "updateBy", "deleted");
    }

    @Test
    void mutableRecordKeepsFullAuditColumnsWithoutLogicalDelete() {
        assertThat(fieldNames(MutableRecordDO.class))
                .containsExactlyInAnyOrderElementsOf(MUTABLE_AUDIT);
    }

    @Test
    void appendOnlyHasCreateAuditOnly() {
        assertThat(fieldNames(AppendOnlyDO.class)).containsExactlyInAnyOrderElementsOf(CREATE_ONLY);
    }

    @Test
    void immutableSnapshotHasCreateAuditOnly() {
        assertThat(fieldNames(ImmutableSnapshotDO.class)).containsExactlyInAnyOrderElementsOf(CREATE_ONLY);
    }

    @Test
    void baseClassesDoNotInheritEachOther() {
        assertThat(BaseDO.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(MutableRecordDO.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(AppendOnlyDO.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(ImmutableSnapshotDO.class.getSuperclass()).isEqualTo(Object.class);
    }

    @Test
    void timeFieldsAreLocalDateTimeOnly() {
        for (Class<?> type : List.of(BaseDO.class, MutableRecordDO.class,
                AppendOnlyDO.class, ImmutableSnapshotDO.class)) {
            Arrays.stream(type.getDeclaredFields())
                    .filter(field -> field.getName().endsWith("At"))
                    .forEach(field -> assertThat(field.getType())
                            .as("%s.%s 必须是 LocalDateTime（TIMESTAMPTZ + UTC 语义）",
                                    type.getSimpleName(), field.getName())
                            .isEqualTo(LocalDateTime.class));
        }
    }

    @Test
    void noTenantFieldsOnAnyDoBase() {
        for (Class<?> type : List.of(BaseDO.class, MutableRecordDO.class,
                AppendOnlyDO.class, ImmutableSnapshotDO.class)) {
            assertThat(fieldNames(type))
                    .as("%s 不得出现租户字段（ADR-029 第 60 条：单企业独立部署）", type.getSimpleName())
                    .noneMatch(name -> name.toLowerCase().contains("tenant"));
        }
    }

    @Test
    void auditFieldAccessorsRoundTrip() {
        MutableRecordDO record = new MutableRecordDO() {
        };
        LocalDateTime now = LocalDateTime.of(2026, 11, 15, 12, 0);

        record.setCreateAt(now);
        record.setUpdateAt(now);
        record.setCreateBy("ci@contract");
        record.setUpdateBy("admin@contract");

        assertThat(record.getCreateAt()).isEqualTo(now);
        assertThat(record.getUpdateAt()).isEqualTo(now);
        assertThat(record.getCreateBy()).isEqualTo("ci@contract");
        assertThat(record.getUpdateBy()).isEqualTo("admin@contract");
    }

    private static List<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
    }
}
