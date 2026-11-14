package com.foundation.account.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.account.infrastructure.persistence.HandoverTodoDO;
import org.apache.ibatis.annotations.Mapper;

/** 交接待办 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface HandoverTodoMapper extends BaseMapper<HandoverTodoDO> {
}
