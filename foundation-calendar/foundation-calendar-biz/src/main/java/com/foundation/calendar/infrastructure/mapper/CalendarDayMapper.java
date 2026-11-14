package com.foundation.calendar.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.foundation.calendar.infrastructure.persistence.CalendarDayDO;
import org.apache.ibatis.annotations.Mapper;

/** 日历日 Mapper（F-09 BAS-18 §18.1）：基础 CRUD 走 {@link BaseMapper}。 */
@Mapper
public interface CalendarDayMapper extends BaseMapper<CalendarDayDO> {
}
