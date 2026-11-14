package com.foundation.audit.application;

import com.foundation.audit.api.ActorType;
import com.foundation.audit.api.AuditOutcome;
import com.foundation.audit.api.RetentionClass;
import com.foundation.audit.domain.AuditLog;
import com.foundation.audit.domain.AuditSeal;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 审计链封印（F-09 BAS-10；GOV-09 / ADR-072 §8）：链连续性与篡改可检出。 */
class AuditSealerTest {

    private static final Instant W1_FROM = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant W1_TO = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant W2_FROM = W1_TO;
    private static final Instant W2_TO = Instant.parse("2026-01-03T00:00:00Z");

    private final TestDoubles.InMemoryAuditLogRepository auditLogs = new TestDoubles.InMemoryAuditLogRepository();
    private final TestDoubles.InMemoryAuditSealRepository seals = new TestDoubles.InMemoryAuditSealRepository();

    @Test
    void sealChainsAcrossWindows() {
        auditLogs.save(audit("adl_a", "app_a", "x", Instant.parse("2026-01-01T01:00:00Z")));
        auditLogs.save(audit("adl_b", "app_a", "y", Instant.parse("2026-01-01T02:00:00Z")));
        AuditSealer sealer = new AuditSealer(auditLogs, seals);

        AuditSeal first = sealer.sealWindow(W1_FROM, W1_TO);

        assertThat(first.prevHash()).isNull();
        assertThat(first.batchHash()).isNotBlank();

        auditLogs.save(audit("adl_c", "app_a", "z", Instant.parse("2026-01-02T03:00:00Z")));
        AuditSeal second = sealer.sealWindow(W2_FROM, W2_TO);

        assertThat(second.prevHash()).isEqualTo(first.batchHash());
        assertThat(seals.latest()).contains(second);
    }

    @Test
    void emptyWindowStillWritesSealToKeepChainContinuous() {
        AuditSealer sealer = new AuditSealer(auditLogs, seals);

        AuditSeal seal = sealer.sealWindow(W1_FROM, W1_TO);

        assertThat(seals.seals).hasSize(1);
        assertThat(seal.batchHash()).isEqualTo(AuditSeal.chainHash(null, List.of()));
    }

    @Test
    void chainHashChangesWhenRowTampered() {
        AuditLog original = audit("adl_a", "app_a", "x", Instant.parse("2026-01-01T01:00:00Z"));
        String base = AuditSeal.chainHash(null, List.of(original));

        AuditLog tampered = audit("adl_a", "app_a", "y", Instant.parse("2026-01-01T01:00:00Z"));

        assertThat(AuditSeal.chainHash(null, List.of(tampered))).isNotEqualTo(base);
    }

    @Test
    void chainHashIsOrderIndependent() {
        AuditLog a = audit("adl_a", "app_a", "x", Instant.parse("2026-01-01T01:00:00Z"));
        AuditLog b = audit("adl_b", "app_a", "y", Instant.parse("2026-01-01T02:00:00Z"));

        assertThat(AuditSeal.chainHash(null, List.of(a, b)))
                .isEqualTo(AuditSeal.chainHash(null, List.of(b, a)));
    }

    @Test
    void prevHashPropagatesIntoHash() {
        AuditLog a = audit("adl_a", "app_a", "x", Instant.parse("2026-01-01T01:00:00Z"));
        List<AuditLog> rows = List.of(a);

        assertThat(AuditSeal.chainHash("prev-1", rows)).isNotEqualTo(AuditSeal.chainHash(null, rows));
    }

    private static AuditLog audit(String id, String app, String action, Instant ts) {
        return new AuditLog(id, app, action, null, null, AuditOutcome.SUCCESS, null, ActorType.USER, "usr_1",
                null, null, null, null, null, "req_1", null, null, RetentionClass.SEC, Map.of(), ts);
    }
}
