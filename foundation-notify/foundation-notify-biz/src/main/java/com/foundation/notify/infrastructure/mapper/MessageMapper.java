package com.foundation.notify.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.notify.infrastructure.persistence.MessageDO;
import org.apache.ibatis.annotations.Mapper;

/** 消息主体 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface MessageMapper extends BaseMapper<MessageDO> {
}
