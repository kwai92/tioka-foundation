package com.foundation.server;

import com.foundation.test.FoundationContainers;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-02 BAS-01：空库执行迁移至最新版本，outbox 表与扩展就位（集成层，Testcontainers PostgreSQL）。
 */
@SpringBootTest
class FlywayBaselineIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void migratesEmptyDatabaseToLatestVersion() {
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void createsOutboxTableWithUnsentPartialIndex() {
        assertThat(jdbc.queryForObject("SELECT to_regclass('public.fdn_outbox')", String.class))
                .isEqualTo("fdn_outbox");
        Integer indexCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'idx_fdn_outbox_unsent'",
                Integer.class);
        assertThat(indexCount).isEqualTo(1);
    }

    @Test
    void installsPgcryptoAndPgTrgmExtensions() {
        Integer extensionCount = jdbc.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname IN ('pgcrypto', 'pg_trgm')", Integer.class);
        assertThat(extensionCount).isEqualTo(2);
    }

    @Test
    void neverDeclaresTenantColumns() {
        Integer tenantColumns = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name LIKE 'fdn_%' AND column_name = 'tenant_id'",
                Integer.class);
        assertThat(tenantColumns).isZero();
    }
}
