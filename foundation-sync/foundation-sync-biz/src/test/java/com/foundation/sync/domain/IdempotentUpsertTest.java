package com.foundation.sync.domain;

import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.api.dto.SyncAction;
import com.foundation.sync.api.dto.SyncConflictType;
import com.foundation.sync.api.dto.SyncSourceStatus;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncKey;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** F-08 BAS-11 §7：幂等落地（新增 / 修改 / 停用 / 移动、非权威越界、锁定字段、唯一键冲突）。 */
class IdempotentUpsertTest {

    private static final String TASK = "syn_1";

    private final InMemoryMasterDataPort port = new InMemoryMasterDataPort();
    private final IdempotentUpserter upserter = new IdempotentUpserter();

    @Test
    void createLandsNewRecordAndEmitsChange() {
        UpsertOutcome outcome = upserter.land(
                List.of(create("u1", Map.of("name", "张三"))), source(true), TASK, port);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.landed()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEmpty();
        assertThat(outcome.changes()).hasSize(1);
        assertThat(outcome.changes().get(0).changeType()).isEqualTo(LandedChange.CREATED);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"))).isPresent();
    }

    @Test
    void landingSameKeyUpsertsInsteadOfDuplicating() {
        upserter.land(List.of(create("u1", Map.of("name", "张三"))), source(true), TASK, port);
        UpsertOutcome second = upserter.land(
                List.of(update("u1", Map.of("name", "张三改"), Set.of())), source(true), TASK, port);

        assertThat(second.created()).isZero();
        assertThat(second.updated()).isEqualTo(1);
        assertThat(port.listBySource(SyncObjectType.PERSON, "FEISHU")).hasSize(1);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1")).orElseThrow().fields())
                .containsEntry("name", "张三改");
    }

    @Test
    void updateWritesOnlyWritableFields() {
        port.create(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"),
                Map.of("name", "张三", "mobile", "138"), null);

        UpsertOutcome outcome = upserter.land(
                List.of(update("u1", Map.of("name", "张三改", "mobile", "138"), Set.of())),
                source(true), TASK, port);

        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(outcome.changes().get(0).changeType()).isEqualTo(LandedChange.UPDATED);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1")).orElseThrow().fields())
                .containsEntry("name", "张三改");
    }

    @Test
    void allFieldsLockedSkipsUpdateAndRecordsConflict() {
        port.create(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"), Map.of("name", "张三"), null);

        UpsertOutcome outcome = upserter.land(
                List.of(update("u1", Map.of("name", "张三改"), Set.of("name"))), source(true), TASK, port);

        assertThat(outcome.updated()).isZero();
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.conflicts()).extracting(SyncConflict::conflictType)
                .containsExactly(SyncConflictType.LOCKED_FIELD);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1")).orElseThrow().fields())
                .containsEntry("name", "张三");
    }

    @Test
    void partialLockWritesRemainingFields() {
        port.create(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"),
                Map.of("name", "张三", "mobile", "138"), null);

        UpsertOutcome outcome = upserter.land(
                List.of(new DiffEntry(SyncObjectType.PERSON, "u1", DiffKind.UPDATE,
                        Map.of("name", "张三改", "mobile", "139"), Set.of("name", "mobile"),
                        Set.of("name"), null)),
                source(true), TASK, port);

        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(outcome.hasConflicts()).isTrue();
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1")).orElseThrow().fields())
                .containsEntry("name", "张三")
                .containsEntry("mobile", "139");
    }

    @Test
    void nonAuthoritySourceCannotWriteMasterData() {
        UpsertOutcome outcome = upserter.land(
                List.of(create("u1", Map.of("name", "张三"))), source(false), TASK, port);

        assertThat(outcome.created()).isZero();
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.conflicts()).extracting(SyncConflict::conflictType)
                .containsExactly(SyncConflictType.AUTHORITY_CONFLICT);
        assertThat(outcome.logs()).extracting(SyncLog::action).containsExactly(SyncAction.SKIP);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"))).isEmpty();
    }

    @Test
    void nonAuthoritySourceMayWriteExternalIdentity() {
        DiffEntry entry = new DiffEntry(SyncObjectType.EXTERNAL_IDENTITY, "id1", DiffKind.CREATE,
                Map.of("openId", "ou_1"), Set.of("openId"), Set.of(), null);

        UpsertOutcome outcome = upserter.land(List.of(entry), source(false), TASK, port);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEmpty();
    }

    @Test
    void duplicateKeyBecomesConflict() {
        port.failCreateFor("dup");

        UpsertOutcome outcome = upserter.land(
                List.of(create("dup", Map.of("name", "张三"))), source(true), TASK, port);

        assertThat(outcome.created()).isZero();
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.conflicts()).extracting(SyncConflict::conflictType)
                .containsExactly(SyncConflictType.DUPLICATE_KEY);
        assertThat(outcome.logs()).extracting(SyncLog::action).containsExactly(SyncAction.ERROR);
    }

    @Test
    void disableMarksExistingRecord() {
        port.create(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1"), Map.of("name", "张三"), null);

        UpsertOutcome outcome = upserter.land(List.of(disable("u1")), source(true), TASK, port);

        assertThat(outcome.disabled()).isEqualTo(1);
        assertThat(outcome.changes().get(0).changeType()).isEqualTo(LandedChange.DISABLED);
        assertThat(port.find(SyncObjectType.PERSON, SyncKey.of("FEISHU", "u1")).orElseThrow().disabled()).isTrue();
    }

    @Test
    void moveChangesParent() {
        port.create(SyncObjectType.ORG_UNIT, SyncKey.of("FEISHU", "o1"),
                Map.of("name", "研发部"), SyncKey.of("FEISHU", "p1"));

        DiffEntry entry = new DiffEntry(SyncObjectType.ORG_UNIT, "o1", DiffKind.MOVE,
                Map.of("name", "研发部"), Set.of(), Set.of(), "p2");

        UpsertOutcome outcome = upserter.land(List.of(entry), source(true), TASK, port);

        assertThat(outcome.moved()).isEqualTo(1);
        assertThat(outcome.changes().get(0).changeType()).isEqualTo(LandedChange.MOVED);
        assertThat(port.find(SyncObjectType.ORG_UNIT, SyncKey.of("FEISHU", "o1")).orElseThrow().parentExternalId())
                .isEqualTo("p2");
    }

    @Test
    void missingLocalRecordForUpdatePropagatesNotFound() {
        assertThatThrownBy(() -> upserter.land(
                List.of(new DiffEntry(SyncObjectType.ORG_UNIT, "missing", DiffKind.MOVE,
                        Map.of(), Set.of(), Set.of(), "p2")),
                source(true), TASK, port))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---------------------------------------------------------------- 造数

    private static DiffEntry create(String externalId, Map<String, Object> fields) {
        return new DiffEntry(SyncObjectType.PERSON, externalId, DiffKind.CREATE, fields,
                fields.keySet(), Set.of(), null);
    }

    private static DiffEntry update(String externalId, Map<String, Object> fields, Set<String> locked) {
        return new DiffEntry(SyncObjectType.PERSON, externalId, DiffKind.UPDATE, fields,
                fields.keySet(), locked, null);
    }

    private static DiffEntry disable(String externalId) {
        return new DiffEntry(SyncObjectType.PERSON, externalId, DiffKind.DISABLE, Map.of(),
                Set.of(), Set.of(), null);
    }

    private static SyncSource source(boolean authority) {
        return new SyncSource("src_1", "SRC", "来源", "FEISHU", authority, null, "AdapterImpl", null,
                SourceCapabilities.none(), Map.of(), SyncSourceStatus.ENABLED, null, null);
    }
}
