package com.foundation.sync.domain;

import com.foundation.sync.api.dto.DiffKind;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.spi.SyncObjectType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** F-08 BAS-11 §6：删除保护阈值（默认停用人员 >5% 或 >50 人、停用组织 >10 个，边界不误判）。 */
class DeleteProtectionPolicyTest {

    private final DeleteProtectionPolicy policy = new DeleteProtectionPolicy();

    @Test
    void emptyDiffPasses() {
        assertThat(policy.evaluate(DiffResult.empty(), 0).pause()).isFalse();
        assertThat(policy.evaluate(null, 100).pause()).isFalse();
    }

    @Test
    void orgUnitDeleteAtThresholdPasses() {
        assertThat(policy.evaluate(diff(SyncObjectType.ORG_UNIT, 10), 100).pause()).isFalse();
    }

    @Test
    void orgUnitDeleteOverThresholdPauses() {
        DeleteProtectionVerdict verdict = policy.evaluate(diff(SyncObjectType.ORG_UNIT, 11), 100);

        assertThat(verdict.pause()).isTrue();
        assertThat(verdict.reason()).isEqualTo(SyncPauseReason.THRESHOLD);
    }

    @Test
    void personDisableAtCountThresholdPasses() {
        assertThat(policy.evaluate(diff(SyncObjectType.PERSON, 50), 10_000).pause()).isFalse();
    }

    @Test
    void personDisableOverCountThresholdPauses() {
        assertThat(policy.evaluate(diff(SyncObjectType.PERSON, 51), 10_000).pause()).isTrue();
    }

    @Test
    void percentExactlyAtThresholdPasses() {
        DeleteProtectionPolicy percentOnly = new DeleteProtectionPolicy(5, 10_000, 10_000);

        // 5 / 100 == 5%：严格大于才暂停，边界放行
        assertThat(percentOnly.evaluate(diff(SyncObjectType.PERSON, 5), 100).pause()).isFalse();
    }

    @Test
    void percentOverThresholdPauses() {
        DeleteProtectionPolicy percentOnly = new DeleteProtectionPolicy(5, 10_000, 10_000);

        assertThat(percentOnly.evaluate(diff(SyncObjectType.PERSON, 6), 100).pause()).isTrue();
    }

    @Test
    void percentRuleSkippedWhenNoExistingPersons() {
        assertThat(policy.evaluate(diff(SyncObjectType.PERSON, 40), 0).pause()).isFalse();
    }

    @Test
    void nonDisableEntriesDoNotCountTowardsThreshold() {
        List<DiffEntry> entries = new ArrayList<>();
        entries.add(entry(SyncObjectType.PERSON, DiffKind.CREATE, "c1"));
        entries.add(entry(SyncObjectType.PERSON, DiffKind.UPDATE, "u1"));
        entries.add(entry(SyncObjectType.PERSON, DiffKind.MOVE, "m1"));

        assertThat(policy.evaluate(DiffResult.of(entries), 100).pause()).isFalse();
    }

    @Test
    void defaultThresholdsAreFiveFiftyTen() {
        assertThat(policy.disablePercent()).isEqualTo(5);
        assertThat(policy.disableCount()).isEqualTo(50);
        assertThat(policy.orgUnitDeleteCount()).isEqualTo(10);
    }

    private static DiffResult diff(SyncObjectType objectType, int count) {
        List<DiffEntry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(entry(objectType, DiffKind.DISABLE, "id-" + index));
        }
        return DiffResult.of(entries);
    }

    private static DiffEntry entry(SyncObjectType objectType, DiffKind kind, String externalId) {
        return new DiffEntry(objectType, externalId, kind, Map.of(), Set.of(), Set.of(), null);
    }
}
