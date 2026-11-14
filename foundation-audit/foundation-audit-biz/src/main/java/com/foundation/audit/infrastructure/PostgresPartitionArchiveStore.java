package com.foundation.audit.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PostgreSQL 月度分区归档存储（F-09 BAS-10 §归档）。
 *
 * <p><b>为何用 {@link DataSource} 原始 JDBC</b>：本类只做分区维护的 DDL（{@code ALTER TABLE … DETACH PARTITION} /
 * {@code DROP TABLE}）与系统目录查询（{@code pg_inherits}/{@code pg_class}），ORM（MyBatis-Plus）无对应能力，
 * 故按 48-CS §4 的例外使用 {@code DataSource} 原始 JDBC，且**不使用** {@code JdbcTemplate}。
 *
 * <p>分区命名约定 {@code fdn_audit_log_YYYY_MM}；上界为「下个月 1 日 00:00 UTC」。约定外的分区名忽略并记 warn。
 */
public class PostgresPartitionArchiveStore implements AuditArchiveStore {

    private static final Logger log = LoggerFactory.getLogger(PostgresPartitionArchiveStore.class);

    private static final String PARENT = "fdn_audit_log";
    private static final String DEFAULT_PARTITION = "fdn_audit_log_default";
    private static final Pattern PARTITION_PATTERN = Pattern.compile("^fdn_audit_log_(\\d{4})_(\\d{2})$");

    private final DataSource dataSource;

    public PostgresPartitionArchiveStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<String> listExpiredPartitions(Instant cutoff) {
        List<String> expired = new ArrayList<>();
        String sql = "SELECT child.relname FROM pg_inherits "
                + "JOIN pg_class parent ON parent.oid = pg_inherits.inhparent "
                + "JOIN pg_class child ON child.oid = pg_inherits.inhrelid "
                + "WHERE parent.relname = '" + PARENT + "'";
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                String partition = rs.getString(1);
                if (DEFAULT_PARTITION.equals(partition)) {
                    continue;
                }
                Instant upper = upperBound(partition);
                if (upper == null) {
                    log.warn("忽略不符合命名约定的审计分区：{}", partition);
                    continue;
                }
                if (!upper.isAfter(cutoff)) {
                    expired.add(partition);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("列出审计过期分区失败：" + e.getMessage(), e);
        }
        return expired;
    }

    @Override
    public void detachAndDrop(String partition) {
        if (partition == null || upperBound(partition) == null) {
            throw new IllegalArgumentException("非法审计分区名：" + partition);
        }
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + PARENT + " DETACH PARTITION " + partition);
            statement.execute("DROP TABLE " + partition);
        } catch (SQLException e) {
            throw new IllegalStateException("归档审计分区失败：" + partition + "，" + e.getMessage(), e);
        }
    }

    /** 解析分区名对应的上界（下月 1 日 00:00 UTC）；不符合约定返回 {@code null}。 */
    private static Instant upperBound(String partition) {
        if (partition == null) {
            return null;
        }
        Matcher matcher = PARTITION_PATTERN.matcher(partition);
        if (!matcher.matches()) {
            return null;
        }
        int year = Integer.parseInt(matcher.group(1));
        int month = Integer.parseInt(matcher.group(2));
        if (month < 1 || month > 12) {
            return null;
        }
        return YearMonth.of(year, month).plusMonths(1).atDay(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
