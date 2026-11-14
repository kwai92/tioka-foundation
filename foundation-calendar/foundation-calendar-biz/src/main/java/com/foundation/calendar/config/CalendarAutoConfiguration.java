package com.foundation.calendar.config;

import com.foundation.calendar.api.CalendarAdminApi;
import com.foundation.calendar.api.CalendarApi;
import com.foundation.calendar.api.CalendarController;
import com.foundation.calendar.application.CalendarAdminService;
import com.foundation.calendar.application.CalendarCache;
import com.foundation.calendar.application.WorkCalendarService;
import com.foundation.calendar.infrastructure.CalendarDayRepository;
import com.foundation.calendar.infrastructure.CalendarRepository;
import com.foundation.calendar.infrastructure.MybatisCalendarDayRepository;
import com.foundation.calendar.infrastructure.MybatisCalendarRepository;
import com.foundation.calendar.infrastructure.mapper.CalendarDayMapper;
import com.foundation.calendar.infrastructure.mapper.CalendarMapper;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 工作日历自动装配（F-09 BAS-18 §18.1）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 缓存 → 应用服务 → {@code -api} 契约 Bean → Controller。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化），缺失时整块跳过；变更事件经 {@link OutboxAppender}
 * 写入 {@code fdn_outbox}（由公共域装配提供）。
 */
@AutoConfiguration
@AutoConfigureAfter(name = {"com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration"})
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties(CalendarProperties.class)
public class CalendarAutoConfiguration {

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public CalendarRepository calendarRepository(CalendarMapper mapper) {
        return new MybatisCalendarRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public CalendarDayRepository calendarDayRepository(CalendarDayMapper mapper) {
        return new MybatisCalendarDayRepository(mapper);
    }

    // ---------- 缓存与读取服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public CalendarCache calendarCache(CalendarRepository calendars, CalendarDayRepository days,
                                       CalendarProperties properties) {
        return new CalendarCache(calendars, days, properties.getDefaultRegion(), properties.getCacheTtl());
    }

    @Bean
    @ConditionalOnMissingBean(CalendarApi.class)
    public CalendarApi calendarApi(CalendarRepository calendars, CalendarCache cache, CalendarProperties properties) {
        return new WorkCalendarService(calendars, cache, properties);
    }

    @Bean
    @ConditionalOnMissingBean(CalendarAdminApi.class)
    public CalendarAdminApi calendarAdminApi(CalendarRepository calendars, CalendarDayRepository days,
                                             CalendarCache cache, OutboxAppender outbox, EventProperties events) {
        return new CalendarAdminService(calendars, days, cache, outbox, events);
    }

    // ---------- REST 控制器 ----------

    @Bean
    @ConditionalOnMissingBean
    public CalendarController calendarController(CalendarApi calendars, CalendarAdminApi admin) {
        return new CalendarController(calendars, admin);
    }
}
