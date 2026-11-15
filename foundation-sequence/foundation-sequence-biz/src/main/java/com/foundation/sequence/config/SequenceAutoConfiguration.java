package com.foundation.sequence.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.foundation.common.api.dict.DictApi;
import com.foundation.sequence.api.SequenceApi;
import com.foundation.sequence.api.SequenceController;
import com.foundation.sequence.api.SequenceRuleApi;
import com.foundation.sequence.api.SequenceRuleController;
import com.foundation.sequence.application.SequenceIdempotencyStore;
import com.foundation.sequence.application.SequenceRuleService;
import com.foundation.sequence.application.SequenceService;
import com.foundation.sequence.infrastructure.MybatisSequenceCounterRepository;
import com.foundation.sequence.infrastructure.MybatisSequenceRuleRepository;
import com.foundation.sequence.infrastructure.SequenceCounterRepository;
import com.foundation.sequence.infrastructure.SequenceRuleRepository;
import com.foundation.sequence.infrastructure.mapper.SequenceMapper;
import com.foundation.sequence.infrastructure.mapper.SequenceRuleMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.time.Clock;

/**
 * 编号与序列自动装配（F-09 BAS-18 §18.2）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus / 注解 SQL，48-CS §4）→ 幂等存储 → 应用服务 → {@code -api} 契约 Bean → Controller。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化），缺失时整块跳过；字典 {@link DictApi} 可选，
 * 缺失时跳过业务码值登记校验（运行时不依赖字典）。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "foundation.server.domains", name = "sequence", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(name = "com.foundation.starter.data.config.FoundationDataAutoConfiguration")
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties(SequenceProperties.class)
public class SequenceAutoConfiguration {

    // ---------- 仓储 ----------

    @Bean
    @ConditionalOnMissingBean
    public SequenceRuleRepository sequenceRuleRepository(SequenceRuleMapper mapper) {
        return new MybatisSequenceRuleRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SequenceCounterRepository sequenceCounterRepository(SequenceMapper mapper) {
        return new MybatisSequenceCounterRepository(mapper);
    }

    // ---------- 幂等存储与应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public SequenceIdempotencyStore sequenceIdempotencyStore(SequenceProperties properties) {
        return new SequenceIdempotencyStore(properties.getIdempotencyTtl());
    }

    @Bean
    @ConditionalOnMissingBean(SequenceApi.class)
    public SequenceApi sequenceApi(SequenceRuleRepository rules, SequenceCounterRepository counters,
                                   SequenceIdempotencyStore idempotency, SequenceProperties properties) {
        return new SequenceService(rules, counters, idempotency, properties, Clock.systemDefaultZone());
    }

    @Bean
    @ConditionalOnMissingBean(SequenceRuleApi.class)
    public SequenceRuleApi sequenceRuleApi(SequenceRuleRepository rules, ObjectProvider<DictApi> dict) {
        return new SequenceRuleService(rules, dict, Clock.systemDefaultZone());
    }

    // ---------- REST 控制器 ----------

    @Bean
    @ConditionalOnMissingBean
    public SequenceController sequenceController(SequenceApi sequences) {
        return new SequenceController(sequences);
    }

    @Bean
    @ConditionalOnMissingBean
    public SequenceRuleController sequenceRuleController(SequenceRuleApi rules) {
        return new SequenceRuleController(rules);
    }
}
