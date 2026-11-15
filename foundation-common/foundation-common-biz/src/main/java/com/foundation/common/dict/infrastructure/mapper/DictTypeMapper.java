package com.foundation.common.dict.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.common.dict.infrastructure.persistence.DictTypeDO;
import org.apache.ibatis.annotations.Mapper;

/** {@code fdn_dict_type} Mapper（简单 CRUD 走 Wrapper，无需自定义 SQL）。 */
@Mapper
public interface DictTypeMapper extends BaseMapper<DictTypeDO> {
}
