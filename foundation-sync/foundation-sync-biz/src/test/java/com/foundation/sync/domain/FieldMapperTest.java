package com.foundation.sync.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.FieldConverter;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-08 BAS-11 §8：字段映射（默认值、必填、转换函数、对象类型过滤）。 */
class FieldMapperTest {

    private final FieldMapper mapper = new FieldMapper(List.of(upper()));

    @Test
    void mapsDeclaredFieldsInOrder() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "user_name", "name", null, null, false),
                mapping(SyncObjectType.PERSON, "mobile_no", "mobile", null, null, false));

        Map<String, Object> mapped = mapper.map(SyncObjectType.PERSON, mappings,
                Map.of("user_name", "张三", "mobile_no", "138"));

        assertThat(mapped).containsExactlyInAnyOrderEntriesOf(Map.of("name", "张三", "mobile", "138"));
        assertThat(mapped.keySet()).containsExactly("name", "mobile");
    }

    @Test
    void defaultFillsMissingValue() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "user_name", "name", "匿名", null, false));

        Map<String, Object> mapped = mapper.map(SyncObjectType.PERSON, mappings, Map.of());

        assertThat(mapped).containsEntry("name", "匿名");
    }

    @Test
    void blankValueTreatedAsMissingAndFilledWithDefault() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "user_name", "name", "匿名", null, false));

        assertThat(mapper.map(SyncObjectType.PERSON, mappings, Map.of("user_name", "  ")))
                .containsEntry("name", "匿名");
    }

    @Test
    void requiredMissingThrowsBadRequest() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "emp_no", "empNo", null, null, true));

        assertThatThrownBy(() -> mapper.map(SyncObjectType.PERSON, mappings, Map.of()))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void optionalMissingIsSkipped() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "mobile_no", "mobile", null, null, false));

        assertThat(mapper.map(SyncObjectType.PERSON, mappings, Map.of())).isEmpty();
    }

    @Test
    void registeredTransformIsApplied() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "user_name", "name", null, "upper", false));

        assertThat(mapper.map(SyncObjectType.PERSON, mappings, Map.of("user_name", "zhang")))
                .containsEntry("name", "ZHANG");
    }

    @Test
    void unknownTransformThrowsBadRequest() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.PERSON, "user_name", "name", null, "not-registered", false));

        assertThatThrownBy(() -> mapper.map(SyncObjectType.PERSON, mappings, Map.of("user_name", "zhang")))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void mappingsOfOtherObjectTypesAreIgnored() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.ORG_UNIT, "dept_name", "name", null, null, false),
                mapping(SyncObjectType.PERSON, "user_name", "name", null, null, false));

        Map<String, Object> mapped = mapper.map(SyncObjectType.PERSON, mappings, Map.of("user_name", "张三"));

        assertThat(mapped).containsExactlyEntriesOf(Map.of("name", "张三"));
    }

    @Test
    void mapsExternalRecordToMappedRecord() {
        List<FieldMapping> mappings = List.of(
                mapping(SyncObjectType.ORG_UNIT, "dept_name", "name", null, null, false));
        ExternalRecord record = new ExternalRecord(SyncObjectType.ORG_UNIT, "o1",
                Map.of("dept_name", "研发部"), "p1", false, null);

        MappedRecord mapped = mapper.map(record, mappings);

        assertThat(mapped.objectType()).isEqualTo(SyncObjectType.ORG_UNIT);
        assertThat(mapped.externalId()).isEqualTo("o1");
        assertThat(mapped.fields()).containsEntry("name", "研发部");
        assertThat(mapped.parentExternalId()).isEqualTo("p1");
        assertThat(mapped.deleted()).isFalse();
    }

    @Test
    void nullExternalFieldsYieldsEmptyMapping() {
        assertThat(mapper.map(SyncObjectType.PERSON, List.of(), null)).isEmpty();
    }

    private static FieldMapping mapping(SyncObjectType objectType, String externalField, String internalField,
                                        String defaultValue, String transform, boolean required) {
        return new FieldMapping("map_1", "src_1", objectType, externalField, internalField,
                defaultValue, transform, required);
    }

    private static FieldConverter upper() {
        return new FieldConverter() {
            @Override
            public String name() {
                return "upper";
            }

            @Override
            public Object convert(Object rawValue) {
                return rawValue == null ? null : String.valueOf(rawValue).toUpperCase();
            }
        };
    }
}
