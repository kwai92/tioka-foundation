package com.foundation.sync.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** F-08 BAS-11 §6：差异算法（新增 / 修改 / 停用 / 移动）与本地锁定字段标注。 */
class DiffPreviewTest {

    private static final String SOURCE = "FEISHU";

    private final InMemoryMasterDataPort port = new InMemoryMasterDataPort();
    private final DiffPreviewService service = new DiffPreviewService(new LocalLockResolver());

    @Test
    void createWhenLocalRecordMissing() {
        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三", "empNo", "E1"))), SOURCE, port, false);

        assertThat(result.countCreated()).isEqualTo(1);
        DiffEntry entry = result.byKind(DiffKind.CREATE).get(0);
        assertThat(entry.externalId()).isEqualTo("u1");
        assertThat(entry.changedFields()).containsExactlyInAnyOrder("name", "empNo");
        assertThat(entry.lockedFields()).isEmpty();
    }

    @Test
    void updateCarriesOnlyChangedFields() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"),
                Map.of("name", "张三", "mobile", "138"), null);

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三改", "mobile", "138"))), SOURCE, port, false);

        assertThat(result.countCreated()).isZero();
        assertThat(result.countUpdated()).isEqualTo(1);
        DiffEntry entry = result.byKind(DiffKind.UPDATE).get(0);
        assertThat(entry.changedFields()).containsExactly("name");
        assertThat(entry.writableFields()).containsExactly("name");
    }

    @Test
    void noChangeProducesNoDiff() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"),
                Map.of("name", "张三", "mobile", "138"), null);

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三", "mobile", "138"))), SOURCE, port, false);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void blankAndNullAreTreatedAsEqualWhenBothEmpty() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"), Map.of("mobile", ""), null);

        Map<String, Object> nullValue = new LinkedHashMap<>();
        nullValue.put("mobile", null);

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", nullValue)), SOURCE, port, false);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void deletedRecordBecomesDisableDiff() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"), Map.of("name", "张三"), null);

        MappedRecord deleted = new MappedRecord(SyncObjectType.PERSON, "u1", Map.of(), null, true);
        DiffResult result = service.preview(SyncObjectType.PERSON, List.of(deleted), SOURCE, port, false);

        assertThat(result.countDisabled()).isEqualTo(1);
        assertThat(result.disabled(SyncObjectType.PERSON)).hasSize(1);
    }

    @Test
    void alreadyDisabledRecordProducesNoSecondDisable() {
        LandedRecord created = port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"),
                Map.of("name", "张三"), null);
        port.disable(SyncObjectType.PERSON, created);

        MappedRecord deleted = new MappedRecord(SyncObjectType.PERSON, "u1", Map.of(), null, true);
        DiffResult result = service.preview(SyncObjectType.PERSON, List.of(deleted), SOURCE, port, false);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void orgUnitParentChangeBecomesMove() {
        port.create(SyncObjectType.ORG_UNIT, SyncKey.of(SOURCE, "o1"),
                Map.of("name", "研发部"), SyncKey.of(SOURCE, "p1"));

        MappedRecord moved = new MappedRecord(SyncObjectType.ORG_UNIT, "o1",
                Map.of("name", "研发部"), "p2", false);
        DiffResult result = service.preview(SyncObjectType.ORG_UNIT, List.of(moved), SOURCE, port, false);

        assertThat(result.countMoved()).isEqualTo(1);
        assertThat(result.countUpdated()).isZero();
        assertThat(result.byKind(DiffKind.MOVE).get(0).parentExternalId()).isEqualTo("p2");
    }

    @Test
    void nonOrgUnitNeverMoves() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"),
                Map.of("name", "张三"), SyncKey.of(SOURCE, "org1"));

        MappedRecord record = new MappedRecord(SyncObjectType.PERSON, "u1",
                Map.of("name", "张三"), "org2", false);
        DiffResult result = service.preview(SyncObjectType.PERSON, List.of(record), SOURCE, port, false);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void fullSnapshotDisablesVanishedRecords() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"), Map.of("name", "张三"), null);
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u2"), Map.of("name", "李四"), null);

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三"))), SOURCE, port, true);

        assertThat(result.countDisabled()).isEqualTo(1);
        assertThat(result.disabled(SyncObjectType.PERSON).get(0).externalId()).isEqualTo("u2");
    }

    @Test
    void incrementalDoesNotDisableVanishedRecords() {
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"), Map.of("name", "张三"), null);
        port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u2"), Map.of("name", "李四"), null);

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三"))), SOURCE, port, false);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    void lockedFieldsAreExcludedFromWritableFields() {
        LandedRecord created = port.create(SyncObjectType.PERSON, SyncKey.of(SOURCE, "u1"),
                Map.of("name", "张三", "mobile", "138"), null);
        port.setLockedFields(SyncObjectType.PERSON, created.localId(), Set.of("name"));

        DiffResult result = service.preview(SyncObjectType.PERSON,
                List.of(person("u1", Map.of("name", "张三改", "mobile", "139"))), SOURCE, port, false);

        DiffEntry entry = result.byKind(DiffKind.UPDATE).get(0);
        assertThat(entry.changedFields()).containsExactlyInAnyOrder("name", "mobile");
        assertThat(entry.lockedFields()).containsExactly("name");
        assertThat(entry.writableFields()).containsExactly("mobile");
        assertThat(entry.hasLockedFields()).isTrue();
    }

    private static MappedRecord person(String externalId, Map<String, Object> fields) {
        return new MappedRecord(SyncObjectType.PERSON, externalId, fields, null, false);
    }
}
