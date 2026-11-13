package com.foundation.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.org.infrastructure.persistence.PersonDO;
import org.apache.ibatis.annotations.Mapper;

/** 人员 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface PersonMapper extends BaseMapper<PersonDO> {
}
