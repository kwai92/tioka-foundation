package com.foundation.app.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.app.infrastructure.persistence.AppDO;
import org.apache.ibatis.annotations.Mapper;

/** {@code fdn_app} Mapper（简单 CRUD 走 Wrapper，无需自定义 SQL）。 */
@Mapper
public interface AppMapper extends BaseMapper<AppDO> {
}
