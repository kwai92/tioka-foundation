package com.foundation.notify.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.notify.infrastructure.persistence.MessageTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/** 渠道模板 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface MessageTemplateMapper extends BaseMapper<MessageTemplateDO> {
}
