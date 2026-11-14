package com.foundation.audit.application;

import com.foundation.audit.domain.AuditLog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 审计归档（F-09 BAS-10 §归档）：按「解绑 + 删除」归档过期分区并写运维审计。 */
class AuditArchiverTest {

    private final TestDoubles.InMemoryArchiveStore store = new TestDoubles.InMemoryArchiveStore();
    private final TestDoubles.InMemoryAuditLogRepository auditLogs = new TestDoubles.InMemoryAuditLogRepository();

    @Test
    void archiveExpiredDropsPartitionsAndWritesAudit() {
        store.expired.addAll(List.of("fdn_audit_log_2023_01", "fdn_audit_log_2023_02"));
        AuditArchiver archiver = new AuditArchiver(store, auditLogs);

        int archived = archiver.archiveExpired(Instant.parse("2024-01-01T00:00:00Z"));

        assertThat(archived).isEqualTo(2);
        assertThat(store.dropped).containsExactly("fdn_audit_log_2023_01", "fdn_audit_log_2023_02");

        List<AuditLog> audits = auditLogs.byAction("audit.partition.archived");
        assertThat(audits).hasSize(2);
        assertThat(audits).allSatisfy(audit -> assertThat(audit.appCode()).isEqualTo("fdn"));
    }

    @Test
    void archiveExpiredWithNoCandidatesDoesNothing() {
        AuditArchiver archiver = new AuditArchiver(store, auditLogs);

        assertThat(archiver.archiveExpired(Instant.parse("2024-01-01T00:00:00Z"))).isZero();
        assertThat(store.dropped).isEmpty();
        assertThat(auditLogs.rows).isEmpty();
    }
}
