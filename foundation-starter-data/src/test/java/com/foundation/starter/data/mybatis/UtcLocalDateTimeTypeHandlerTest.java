package com.foundation.starter.data.mybatis;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UtcLocalDateTimeTypeHandler} 的读写往返单测（48-CS §4：DB {@code TIMESTAMPTZ} ↔ Java {@code LocalDateTime} UTC 语义）。
 *
 * <p>为什么在离线单测里覆盖：真实的 TIMESTAMPTZ 往返由 {@code MqOutboxIT} 在 CI（需 Docker）坐实，
 * 但本机的时区/驱动组合无法复现 pgjdbc 行为，故此处用 Mockito 固定 JDBC 契约，
 * 把「偏移归一到 UTC」这条核心语义变成**每次构建都跑**的确定性断言，避免只在 CI 才暴露回归。
 */
class UtcLocalDateTimeTypeHandlerTest {

    private final UtcLocalDateTimeTypeHandler handler = new UtcLocalDateTimeTypeHandler();

    @Test
    void 写入时按UTC偏移交给驱动() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        LocalDateTime utcWallTime = LocalDateTime.of(2026, 11, 18, 3, 4, 5);

        handler.setNonNullParameter(ps, 1, utcWallTime, JdbcType.TIMESTAMP_WITH_TIMEZONE);

        // 必须是 OffsetDateTime(UTC)，不能是 LocalDateTime —— 后者写 TIMESTAMPTZ 列会被驱动按会话时区解释
        verify(ps).setObject(eq(1), eq(OffsetDateTime.of(utcWallTime, ZoneOffset.UTC)));
    }

    @Test
    void 读取时把任意时区偏移归一到UTC墙上时间() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        // +08:00 的 12:00 与 UTC 的 04:00 是同一时刻；DO 字段口径是 UTC 语义，故应得到 04:00
        when(rs.getObject("create_at", OffsetDateTime.class)).thenReturn(
                OffsetDateTime.of(LocalDateTime.of(2026, 11, 18, 12, 0, 0), ZoneOffset.ofHours(8)));

        assertThat(handler.getNullableResult(rs, "create_at"))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 4, 0, 0));
    }

    @Test
    void 按列下标读取同样归一到UTC() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject(3, OffsetDateTime.class)).thenReturn(
                OffsetDateTime.of(LocalDateTime.of(2026, 11, 18, 12, 0, 0), ZoneOffset.ofHours(8)));

        assertThat(handler.getNullableResult(rs, 3))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 4, 0, 0));
    }

    @Test
    void 存储过程出参同样归一到UTC() throws Exception {
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getObject(2, OffsetDateTime.class)).thenReturn(
                OffsetDateTime.of(LocalDateTime.of(2026, 11, 18, 12, 0, 0), ZoneOffset.ofHours(8)));

        assertThat(handler.getNullableResult(cs, 2))
                .isEqualTo(LocalDateTime.of(2026, 11, 18, 4, 0, 0));
    }

    @Test
    void 列为NULL时返回null而非抛NPE() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getObject("sent_at", OffsetDateTime.class)).thenReturn(null);

        assertThat(handler.getNullableResult(rs, "sent_at")).isNull();
    }

    @Test
    void 已是UTC的列值原样保留() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        LocalDateTime expected = LocalDateTime.of(2026, 11, 18, 4, 0, 0);
        when(rs.getObject("create_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.of(expected, ZoneOffset.UTC));

        assertThat(handler.getNullableResult(rs, "create_at")).isEqualTo(expected);
    }
}
