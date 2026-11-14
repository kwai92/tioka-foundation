package com.foundation.audit.application;

import com.foundation.audit.api.AuditPageView;
import com.foundation.audit.api.AuditQuery;
import com.foundation.audit.api.AuditReportItem;
import com.foundation.audit.api.AuditView;
import com.foundation.audit.config.AuditProperties;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审计应用服务（F-09 BAS-10 §上报 / §查询 / §导出）：上报幂等、应用范围过滤、导出写审计。 */
class AuditServiceTest {

    private final TestDoubles.InMemoryAuditLogRepository auditLogs = new TestDoubles.InMemoryAuditLogRepository();
    private final TestDoubles.InMemoryAuditReportReceiptRepository receipts =
            new TestDoubles.InMemoryAuditReportReceiptRepository();
    private final AuditProperties properties = new AuditProperties();

    private AuditService service;

    @BeforeEach
    void setUp() {
        service = new AuditService(auditLogs, receipts, properties);
    }

    @Test
    void reportIsIdempotentByKey() {
        AuditReportItem item = item("app_a", "user.create", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z"));

        int first = service.report("key-1", List.of(item));
        int second = service.report("key-1", List.of(item));

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(1);
        assertThat(auditLogs.rows).hasSize(1);
    }

    @Test
    void reportWithoutKeyInsertsEveryTime() {
        AuditReportItem item = item("app_a", "user.create", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z"));

        service.report(null, List.of(item));
        service.report(null, List.of(item));

        assertThat(auditLogs.rows).hasSize(2);
    }

    @Test
    void reportEmptyReturnsZero() {
        assertThat(service.report("key", List.of())).isZero();
        assertThat(auditLogs.rows).isEmpty();
    }

    @Test
    void reportOverBatchLimitIsRejected() {
        properties.setReportMaxBatch(2);
        List<AuditReportItem> events = List.of(
                item("app_a", "a", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z")),
                item("app_a", "b", "SUCCESS", Instant.parse("2026-01-01T00:00:01Z")),
                item("app_a", "c", "SUCCESS", Instant.parse("2026-01-01T00:00:02Z")));

        assertThatThrownBy(() -> service.report("key", events))
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void queryOnlyReturnsRowsWithinAppScope() {
        service.report(null, List.of(
                item("app_a", "x", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z")),
                item("app_b", "y", "SUCCESS", Instant.parse("2026-01-01T00:00:01Z")),
                item("app_c", "z", "SUCCESS", Instant.parse("2026-01-01T00:00:02Z"))));

        AuditPageView scoped = service.query(query(), Set.of("app_a"));
        assertThat(scoped.items()).extracting(AuditView::appCode).containsExactly("app_a");

        AuditPageView all = service.query(query(), null);
        assertThat(all.items()).hasSize(3);
    }

    @Test
    void queryScopeNotCoveringFilteredAppReturnsEmpty() {
        service.report(null, List.of(item("app_b", "y", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z"))));

        AuditQuery filtered = new AuditQuery("app_b", null, null, null, null, null, null, null, null, 20);

        assertThat(service.query(filtered, Set.of("app_a")).items()).isEmpty();
    }

    @Test
    void queryReturnsNextCursorWhenPageIsFull() {
        service.report(null, List.of(
                item("app_a", "x", "SUCCESS", Instant.parse("2026-01-01T00:00:00Z")),
                item("app_a", "y", "SUCCESS", Instant.parse("2026-01-01T00:00:01Z"))));

        AuditPageView firstPage = service.query(new AuditQuery(null, null, null, null, null, null,
                null, null, null, 2), null);

        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.nextCursor()).isNotBlank();

        AuditPageView secondPage = service.query(new AuditQuery(null, null, null, null, null, null,
                null, null, firstPage.nextCursor(), 2), null);
        assertThat(secondPage.items()).isEmpty();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void exportReturnsRowsAndWritesExportAudit() {
        service.report(null, List.of(item("app_a", "user.create", "SUCCESS",
                Instant.parse("2026-01-01T00:00:00Z"))));

        List<AuditView> exported = service.export(query(), Set.of("app_a"), "usr_admin");

        assertThat(exported).hasSize(1);
        List<com.foundation.audit.domain.AuditLog> exportAudits = auditLogs.byAction("audit.export");
        assertThat(exportAudits).hasSize(1);
        assertThat(exportAudits.get(0).actorId()).isEqualTo("usr_admin");
        assertThat(exportAudits.get(0).details()).containsKey("fileHash");
    }

    private static AuditQuery query() {
        return new AuditQuery(null, null, null, null, null, null, null, null, null, 20);
    }

    private static AuditReportItem item(String appCode, String action, String result, Instant at) {
        return new AuditReportItem("evt_" + action, at, appCode, action, "order:ord_1", result,
                "usr_1", "per_1", "trace_1", "detail", 12L);
    }
}
