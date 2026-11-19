package com.foundation.starter.data.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.foundation.starter.data.mybatis.AuditMetaObjectHandler;
import com.foundation.starter.data.mybatis.UtcLocalDateTimeTypeHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import com.foundation.starter.core.condition.ConditionalOnFoundationCapability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * 数据访问自动装配（F-02 BAS-24 §foundation-starter-data 雏形）。
 *
 * <p>约定：
 * <ul>
 *   <li>ORM 只用 MyBatis-Plus（48-CS §4）；Mapper 接口标 {@link Mapper}，置于
 *       {@code **.infrastructure.mapper}，自定义 SQL 放 {@code resources/mapper/**}{@code /*.xml}；</li>
 *   <li>实体继承 {@link com.foundation.starter.data.BaseDO} 获得审计字段与逻辑删；</li>
 *   <li>分页统一走 MP {@code PaginationInnerInterceptor}，不手写 {@code LIMIT}（48-CS §5）。</li>
 * </ul>
 *
 * <p>依赖 {@link SqlSessionFactory}：未启用数据源的上下文（如纯配置绑定测试）整块跳过。
 */
@AutoConfiguration(after = MybatisPlusAutoConfiguration.class)
@ConditionalOnFoundationCapability("data")
@ConditionalOnBean(SqlSessionFactory.class)
@MapperScan(basePackages = "com.foundation", annotationClass = Mapper.class)
public class FoundationDataAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    /**
     * 全局注册 {@code TIMESTAMPTZ} ↔ {@link LocalDateTime}（UTC 语义）桥接（48-CS §4）。
     *
     * <p>pgjdbc 拒绝把 {@code TIMESTAMPTZ} 直接读成 {@link LocalDateTime}，缺这层桥接则**任何读取
     * 含时间列的 DO 都会在运行期失败**（实证 {@code MqOutboxIT} 读 {@code fdn_outbox.create_at} 即抛
     * {@code PSQLException: Cannot convert the column of type TIMESTAMPTZ to requested type
     * java.time.LocalDateTime}）。以类型级注册覆盖 {@code TIMESTAMP} 与 {@code TIMESTAMP_WITH_TIMEZONE}
     * 两种 JdbcType，使全部 DO 的 {@code LocalDateTime} 字段自动生效，无需逐字段标注。
     *
     * <p><b>刻意不加 {@code @ConditionalOnMissingBean(ConfigurationCustomizer.class)}</b>：该条件是
     * 按**类型**匹配的，接入方注册任意用途的 customizer（例如仅为了配驼峰映射）都会让本桥接被静默跳过，
     * 退化成「时间列读取在运行期炸」的半接线状态。而 {@code TypeHandlerRegistry.register} 本身是覆盖式、
     * 且各方实现语义一致（都按 UTC 归一），重复注册无害，故此处无条件注册以保证平台约定必然生效。
     * 关闭方式只有一个显式开关：{@code foundation.data.enabled=false}（整块自动配置不装配）。
     */
    @Bean
    public ConfigurationCustomizer utcLocalDateTimeTypeHandlerCustomizer() {
        return configuration -> {
            UtcLocalDateTimeTypeHandler handler = new UtcLocalDateTimeTypeHandler();
            configuration.getTypeHandlerRegistry().register(LocalDateTime.class, handler);
            configuration.getTypeHandlerRegistry().register(LocalDateTime.class, JdbcType.TIMESTAMP, handler);
            configuration.getTypeHandlerRegistry().register(LocalDateTime.class, JdbcType.TIMESTAMP_WITH_TIMEZONE, handler);
        };
    }
}
