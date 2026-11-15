package com.foundation.starter.mq.outbox;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@code fdn_outbox} Mapper（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>自定义 SQL 写在 {@code mapper/OutboxMapper.xml}：PostgreSQL 的 {@code jsonb} 列须显式
 * {@code CAST(#{payload} AS jsonb)}，不能依赖 {@code setString}。
 */
@Mapper
public interface OutboxMapper extends BaseMapper<OutboxDO> {

    /** 插入一条事件（{@code payload} 经 CAST 写入 jsonb）。 */
    int insertOutbox(OutboxDO row);

    /** 取一批 {@code status = 'NEW'} 的事件（按 {@code create_at} 升序）。 */
    List<OutboxDO> selectPending(@Param("limit") int limit);

    /** 置 {@code SENT} 并写 {@code sent_at}。 */
    int markSent(@Param("id") String id);

    /** 失败流转：{@code dead} 为真置 {@code DEAD}，否则置 {@code NEW}；两者均递增 {@code retry_count} 并清空 {@code sent_at}。 */
    int markRetry(@Param("id") String id, @Param("dead") boolean dead);
}
