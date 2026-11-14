package com.foundation.calendar.infrastructure;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.foundation.calendar.domain.Calendar;
import com.foundation.calendar.domain.CalendarSource;
import com.foundation.calendar.domain.CalendarStatus;
import com.foundation.calendar.infrastructure.mapper.CalendarMapper;
import com.foundation.calendar.infrastructure.persistence.CalendarDO;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** 工作日历头仓储的 MyBatis-Plus 实现（48-CS §4）。 */
public class MybatisCalendarRepository implements CalendarRepository {

    private final CalendarMapper mapper;

    public MybatisCalendarRepository(CalendarMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<Calendar> findByRegionYear(String regionCode, int year) {
        CalendarDO row = mapper.selectOne(Wrappers.<CalendarDO>lambdaQuery()
                .eq(CalendarDO::getRegionCode, regionCode)
                .eq(CalendarDO::getYear, year)
                .last("limit 1"));
        return Optional.ofNullable(row).map(MybatisCalendarRepository::toDomain);
    }

    @Override
    public List<Calendar> list(String regionCode, Integer year) {
        List<CalendarDO> rows = mapper.selectList(Wrappers.<CalendarDO>lambdaQuery()
                .eq(regionCode != null && !regionCode.isBlank(), CalendarDO::getRegionCode, regionCode)
                .eq(year != null, CalendarDO::getYear, year)
                .orderByAsc(CalendarDO::getRegionCode)
                .orderByAsc(CalendarDO::getYear));
        return rows.stream().map(MybatisCalendarRepository::toDomain).toList();
    }

    @Override
    public void insert(Calendar calendar) {
        CalendarDO row = new CalendarDO();
        row.setId(calendar.id());
        row.setRegionCode(calendar.regionCode());
        row.setYear(calendar.year());
        row.setName(calendar.name());
        row.setSource(calendar.source().name());
        row.setStatus(calendar.status().name());
        LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        row.setCreateAt(calendar.createAt() == null ? now : calendar.createAt());
        row.setUpdateAt(calendar.updateAt() == null ? row.getCreateAt() : calendar.updateAt());
        mapper.insert(row);
    }

    @Override
    public void update(Calendar calendar) {
        CalendarDO row = new CalendarDO();
        row.setId(calendar.id());
        row.setName(calendar.name());
        row.setSource(calendar.source().name());
        row.setStatus(calendar.status().name());
        row.setUpdateAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        mapper.updateById(row);
    }

    private static Calendar toDomain(CalendarDO row) {
        return new Calendar(row.getId(), row.getRegionCode(),
                row.getYear() == null ? 0 : row.getYear(), row.getName(),
                CalendarSource.of(row.getSource()), CalendarStatus.of(row.getStatus()),
                row.getCreateAt(), row.getUpdateAt());
    }
}
