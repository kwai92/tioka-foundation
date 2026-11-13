package com.foundation.starter.data.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.foundation.starter.data.mybatis.AuditMetaObjectHandler;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

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
}
