package com.foundation.sequence.infrastructure.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 序列取号 Mapper（F-09 BAS-18 §18.2）。
 *
 * <p>{@code fdn_sequence} 主键为复合键 {@code (rule_code, period_key)}，MyBatis-Plus 单列主键不适用，
 * 故按 48-CS §4 以注解 SQL 直接表达；取号以数据库为单一权威（一条原子语句完成行锁与自增），
 * 多实例天然唯一、允许跳号。
 */
@Mapper
public interface SequenceMapper {

    /**
     * 原子取号并返回递增后的当前值：无行则插入、有行则累加 {@code step}。
     *
     * <p>PostgreSQL {@code INSERT … ON CONFLICT … DO UPDATE … RETURNING} 由 JDBC 查询通道返回结果集。
     */
    @Select("""
            INSERT INTO fdn_sequence (rule_code, period_key, current, update_at)
            VALUES (#{ruleCode}, #{periodKey}, #{step}, now())
            ON CONFLICT (rule_code, period_key)
            DO UPDATE SET current = fdn_sequence.current + #{step}, update_at = now()
            RETURNING current
            """)
    Long nextValue(@Param("ruleCode") String ruleCode, @Param("periodKey") String periodKey,
                   @Param("step") long step);

    /** 当前周期已用值；无记录返回 {@code null}。 */
    @Select("SELECT current FROM fdn_sequence WHERE rule_code = #{ruleCode} AND period_key = #{periodKey}")
    Long currentValue(@Param("ruleCode") String ruleCode, @Param("periodKey") String periodKey);
}
