package com.foundation.starter.data.mybatis;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * {@code TIMESTAMPTZ} ↔ {@link LocalDateTime}（语义 UTC）桥接（48-CS §4 行级字段口径）。
 *
 * <p><b>为什么平台必须有这一层</b>：48-CS §4 规定「数据库 {@code TIMESTAMPTZ}、Java 字段
 * {@link LocalDateTime}（UTC 语义）」，并由 ArchUnit 规则 {@code doTimeFieldsAreLocalDateTime}
 * 强制字段类型；但 pgjdbc **拒绝**把 {@code TIMESTAMPTZ} 直接读成 {@link LocalDateTime}，会抛
 * {@code PSQLException: Cannot convert the column of type TIMESTAMPTZ to requested type
 * java.time.LocalDateTime}。缺了这层桥接，**任何读取含时间列的 DO 都会在运行期失败**
 * （实证：{@code MqOutboxIT} 读 {@code fdn_outbox.create_at} 即炸），而写入方向由列默认值
 * {@code now()} 掩盖，故长期未被发现。
 *
 * <p>实现口径：读时经 {@link OffsetDateTime} 中转并按 {@code withOffsetSameInstant(UTC)} 归一到
 * UTC 墙上时间；写时按 UTC 偏移回写。与派生应用 Tioka 的同名 handler（{@code ENG-11}）语义一致。
 *
 * <p>注册方式见 {@code FoundationDataAutoConfiguration#utcLocalDateTimeTypeHandlerCustomizer()}：
 * 以类型级注册覆盖 {@code TIMESTAMP} 与 {@code TIMESTAMP_WITH_TIMEZONE} 两种 JdbcType，
 * 使全部 DO 的 {@code LocalDateTime} 字段自动生效，无需逐字段标注 {@code @TableField(typeHandler=...)}。
 */
@MappedTypes(LocalDateTime.class)
@MappedJdbcTypes(value = {JdbcType.TIMESTAMP, JdbcType.TIMESTAMP_WITH_TIMEZONE}, includeNullJdbcType = true)
public class UtcLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter.atOffset(ZoneOffset.UTC));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return toUtcLocal(rs.getObject(columnName, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return toUtcLocal(rs.getObject(columnIndex, OffsetDateTime.class));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return toUtcLocal(cs.getObject(columnIndex, OffsetDateTime.class));
    }

    /** {@code null} 安全：列为 NULL 时返回 {@code null}，不抛 NPE。 */
    private static LocalDateTime toUtcLocal(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
