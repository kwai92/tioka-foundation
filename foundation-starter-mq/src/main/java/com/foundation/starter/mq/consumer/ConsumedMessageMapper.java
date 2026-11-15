package com.foundation.starter.mq.consumer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@code fdn_consumed} Mapper（F-02 BAS-24 §foundation-starter-mq）。
 *
 * <p>自定义 SQL 写在 {@code mapper/ConsumedMessageMapper.xml}：{@code ON CONFLICT DO NOTHING}
 * 保证并发下的幂等（影响行数 &gt; 0 即首次）。
 */
@Mapper
public interface ConsumedMessageMapper extends BaseMapper<ConsumedMessageDO> {

    /** 幂等登记；返回影响行数（0=已消费）。 */
    int insertConsumed(@Param("consumer") String consumer, @Param("messageId") String messageId);

    /** 清理早于 {@code hours} 小时的记录。 */
    int deleteOlderThan(@Param("hours") int hours);
}
