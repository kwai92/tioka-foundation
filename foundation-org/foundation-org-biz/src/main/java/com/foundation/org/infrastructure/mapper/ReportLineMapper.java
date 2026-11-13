package com.foundation.org.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.org.infrastructure.persistence.ReportLineDO;
import org.apache.ibatis.annotations.Mapper;

/** 汇报关系 Mapper（简单 CRUD 走 {@link BaseMapper}，48-CS §4）。 */
@Mapper
public interface ReportLineMapper extends BaseMapper<ReportLineDO> {
}
