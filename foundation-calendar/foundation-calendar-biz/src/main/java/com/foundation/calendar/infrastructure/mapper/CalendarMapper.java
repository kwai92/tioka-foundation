package com.foundation.calendar.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.calendar.infrastructure.persistence.CalendarDO;
import org.apache.ibatis.annotations.Mapper;

/** 工作日历头 Mapper（F-09 BAS-18 §18.1）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface CalendarMapper extends BaseMapper<CalendarDO> {
}
