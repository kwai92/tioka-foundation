package com.foundation.calendar.infrastructure;

import com.foundation.calendar.domain.Calendar;

import java.util.List;
import java.util.Optional;

/** 工作日历头仓储（F-09 BAS-18 §18.1）：持久化边界，实现走 MyBatis-Plus（48-CS §4）。 */
public interface CalendarRepository {

    /** 按「区域 + 年度」读取日历头。 */
    Optional<Calendar> findByRegionYear(String regionCode, int year);

    /** 列表：区域 / 年度均可空表示不过滤。 */
    List<Calendar> list(String regionCode, Integer year);

    /** 追加一条日历头。 */
    void insert(Calendar calendar);

    /** 更新日历头（按 ID）。 */
    void update(Calendar calendar);
}
