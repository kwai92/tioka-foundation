package com.foundation.account.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** F-04 BAS-03 §6：对账差异分类与阈值保护。 */
class ReconcileDiffTest {

    private static FoundationAccountSnapshot foundation(String id, String loginName, String kcId,
                                                       boolean expectedEnabled, List<String> requiredActions) {
        return new FoundationAccountSnapshot(id, loginName, kcId, expectedEnabled, requiredActions);
    }

    private static KeycloakAccountSnapshot keycloak(String kcId, String username, boolean enabled,
                                                    List<String> requiredActions) {
        return new KeycloakAccountSnapshot(kcId, username, enabled, requiredActions);
    }

    @Test
    void detectsEnabledAndUsernameAndRequiredActionsDrift() {
        ReconcileDiff diff = ReconcileDiff.compute(
                List.of(foundation("acc_1", "e1001", "kc_1", true, List.of("UPDATE_PASSWORD"))),
                List.of(keycloak("kc_1", "e1001-renamed", false, List.of())));

        assertThat(diff.drifts()).extracting(DriftItem::kind)
                .containsExactlyInAnyOrder(DriftKind.ENABLED_MISMATCH, DriftKind.USERNAME_MISMATCH,
                        DriftKind.REQUIRED_ACTIONS_MISMATCH);
        assertThat(diff.drifted()).isEqualTo(3);
    }

    @Test
    void detectsMissingInKeycloakWhenNotPushedOrAbsent() {
        ReconcileDiff diff = ReconcileDiff.compute(
                List.of(foundation("acc_1", "e1001", null, true, List.of()),
                        foundation("acc_2", "e1002", "kc_2", true, List.of())),
                List.of());

        assertThat(diff.drifts()).extracting(DriftItem::kind)
                .containsOnly(DriftKind.MISSING_IN_KEYCLOAK);
        assertThat(diff.drifted()).isEqualTo(2);
    }

    @Test
    void missingInKeycloakIsNotFixableByReconcile() {
        ReconcileDiff diff = ReconcileDiff.compute(
                List.of(foundation("acc_1", "e1001", null, true, List.of())),
                List.of());

        assertThat(diff.fixable()).isEmpty();
    }

    @Test
    void orphanInKeycloakFlaggedButClientServiceAccountIgnored() {
        ReconcileDiff diff = ReconcileDiff.compute(
                List.of(foundation("acc_1", "e1001", "kc_1", true, List.of())),
                List.of(keycloak("kc_1", "e1001", true, List.of()),
                        keycloak("kc_orphan", "manual-user", true, List.of()),
                        keycloak("kc_client", "service-account-foundation-admin", true, List.of())));

        assertThat(diff.drifts()).hasSize(1);
        DriftItem orphan = diff.drifts().get(0);
        assertThat(orphan.kind()).isEqualTo(DriftKind.ORPHAN_IN_KEYCLOAK);
        assertThat(orphan.loginName()).isEqualTo("manual-user");
        assertThat(orphan.accountId()).isNull();
        assertThat(orphan.fixable()).isTrue();
    }

    @Test
    void noDriftWhenSnapshotsAgree() {
        ReconcileDiff diff = ReconcileDiff.compute(
                List.of(foundation("acc_1", "e1001", "kc_1", true, List.of("UPDATE_PASSWORD"))),
                List.of(keycloak("kc_1", "e1001", true, List.of("UPDATE_PASSWORD"))));

        assertThat(diff.drifted()).isZero();
        assertThat(diff.exceedsThreshold(1, 5, 50)).isFalse();
    }

    @Test
    void thresholdByPercent() {
        List<DriftItem> five = List.of(
                new DriftItem("acc_1", "e1", DriftKind.ENABLED_MISMATCH, ""),
                new DriftItem("acc_2", "e2", DriftKind.ENABLED_MISMATCH, ""),
                new DriftItem("acc_3", "e3", DriftKind.ENABLED_MISMATCH, ""),
                new DriftItem("acc_4", "e4", DriftKind.ENABLED_MISMATCH, ""),
                new DriftItem("acc_5", "e5", DriftKind.ENABLED_MISMATCH, ""));
        List<DriftItem> six = new java.util.ArrayList<>(five);
        six.add(new DriftItem("acc_6", "e6", DriftKind.ENABLED_MISMATCH, ""));

        assertThat(new ReconcileDiff(five).exceedsThreshold(100, 5, 0)).isFalse();
        assertThat(new ReconcileDiff(six).exceedsThreshold(100, 5, 0)).isTrue();
    }

    @Test
    void thresholdByCount() {
        List<DriftItem> items = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(i -> new DriftItem("acc_" + i, "e" + i, DriftKind.ENABLED_MISMATCH, ""))
                .toList();

        assertThat(new ReconcileDiff(items).exceedsThreshold(10000, 0, 50)).isTrue();
    }

    @Test
    void noDriftNeverExceedsThreshold() {
        assertThat(new ReconcileDiff(List.of()).exceedsThreshold(10, 1, 1)).isFalse();
    }
}
