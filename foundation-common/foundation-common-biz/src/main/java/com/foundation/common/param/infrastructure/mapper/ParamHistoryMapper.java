package com.foundation.common.param.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.common.param.infrastructure.persistence.ParamHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/** {@code fdn_param_history} Mapper（简单 CRUD 走 Wrapper，无需自定义 SQL）。 */
@Mapper
public interface ParamHistoryMapper extends BaseMapper<ParamHistoryDO> {
}
