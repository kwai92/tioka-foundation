package com.foundation.audit.application;

import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.AccountType;
import com.foundation.account.api.dto.AccountView;
import com.foundation.audit.api.LoginLogFailureStat;
import com.foundation.audit.api.LoginLogPageView;
import com.foundation.audit.api.LoginLogQuery;
import com.foundation.audit.config.AuditProperties;
import com.foundation.audit.domain.LoginLog;
import com.foundation.common.api.keycloak.KeycloakLoginEvent;
import com.foundation.common.api.keycloak.KeycloakLoginEventAdmin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 登录日志应用服务（F-09 BAS-10 §登录日志）：Keycloak 采集幂等、账号回填、失败统计。 */
class LoginLogServiceTest {

    private static final Instant T1 = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-01T10:05:00Z");

    private final TestDoubles.InMemoryLoginLogRepository logs = new TestDoubles.InMemoryLoginLogRepository();
    private final TestDoubles.StubKeycloakLoginEventAdmin source = new TestDoubles.StubKeycloakLoginEventAdmin();
    private final TestDoubles.StubAccountApi accounts = new TestDoubles.StubAccountApi();
    private final AuditProperties properties = new AuditProperties();

    private LoginLogService service;

    @BeforeEach
    void setUp() {
        accounts.byLoginName.put("alice", new AccountView("acc_1", "alice", "per_1", "kc_1",
                AccountStatus.NORMAL, AccountType.PERSONAL, null, 1L));
        service = new LoginLogService(logs, TestDoubles.<KeycloakLoginEventAdmin>provider(source),
                TestDoubles.provider(accounts), properties);
    }

    @Test
    void collectIngestsSuccessAndFailureAndIsIdempotent() {
        source.events.add(new KeycloakLoginEvent("evt_1", KeycloakLoginEvent.TYPE_LOGIN, "client-portal",
                "kc_1", "alice", null, "oidc:keycloak", "10.0.0.1", T1));
        source.events.add(new KeycloakLoginEvent("evt_2", KeycloakLoginEvent.TYPE_LOGIN_ERROR, "client-portal",
                "kc_9", "bob", "invalid_credentials", null, "10.0.0.2", T2));

        int added = service.collect();

        assertThat(added).isEqualTo(2);
        assertThat(logs.rows).hasSize(2);

        LoginLog alice = logs.rows.stream().filter(row -> "alice".equals(row.loginName())).findFirst().orElseThrow();
        assertThat(alice.success()).isTrue();
        assertThat(alice.authMethod()).isEqualTo("oidc:keycloak");
        assertThat(alice.accountId()).isEqualTo("acc_1");
        assertThat(alice.personId()).isEqualTo("per_1");

        LoginLog bob = logs.rows.stream().filter(row -> "bob".equals(row.loginName())).findFirst().orElseThrow();
        assertThat(bob.success()).isFalse();
        assertThat(bob.authMethod()).isEqualTo("password");
        assertThat(bob.failureReason()).isEqualTo("invalid_credentials");

        assertThat(service.collect()).isZero();
        assertThat(logs.rows).hasSize(2);
    }

    @Test
    void collectBackfillsLastLoginOnlyForSuccess() {
        source.events.add(new KeycloakLoginEvent("evt_1", KeycloakLoginEvent.TYPE_LOGIN, "client-portal",
                "kc_1", "alice", null, "password", "10.0.0.1", T1));
        source.events.add(new KeycloakLoginEvent("evt_2", KeycloakLoginEvent.TYPE_LOGIN_ERROR, "client-portal",
                "kc_1", "alice", "invalid_credentials", "password", "10.0.0.1", T2));

        service.collect();

        assertThat(accounts.recordedLogins).containsExactly("alice");
        assertThat(accounts.lastLoginAt.get("alice")).isEqualTo(T1);
    }

    @Test
    void collectWithoutKeycloakSourceDegradesToZero() {
        LoginLogService degraded = new LoginLogService(logs,
                TestDoubles.<KeycloakLoginEventAdmin>provider(null), TestDoubles.provider(accounts), properties);

        assertThat(degraded.collect()).isZero();
        assertThat(logs.rows).isEmpty();
    }

    @Test
    void pageReturnsItemsAndTotal() {
        source.events.add(new KeycloakLoginEvent("evt_1", KeycloakLoginEvent.TYPE_LOGIN, "client-portal",
                "kc_1", "alice", null, "password", "10.0.0.1", T1));
        source.events.add(new KeycloakLoginEvent("evt_2", KeycloakLoginEvent.TYPE_LOGIN_ERROR, "client-portal",
                "kc_9", "bob", "invalid_credentials", "password", "10.0.0.2", T2));
        service.collect();

        LoginLogPageView page = service.page(new LoginLogQuery(null, null, null, null, null, 1, 20));

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.page()).isEqualTo(1);
    }

    @Test
    void failureStatsAggregateFailuresByLoginName() {
        source.events.add(new KeycloakLoginEvent("evt_2", KeycloakLoginEvent.TYPE_LOGIN_ERROR, "client-portal",
                "kc_9", "bob", "invalid_credentials", "password", "10.0.0.2", T2));
        source.events.add(new KeycloakLoginEvent("evt_3", KeycloakLoginEvent.TYPE_LOGIN_ERROR, "client-portal",
                "kc_9", "bob", "invalid_credentials", "password", "10.0.0.2", T1));
        service.collect();

        List<LoginLogFailureStat> stats = service.failureStats("bob", null, null);

        assertThat(stats).containsExactly(new LoginLogFailureStat("bob", 2L));
    }
}
