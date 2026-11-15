package com.foundation.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.dict.DictApi;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.common.api.param.ParamApi;
import com.foundation.common.dict.application.DictService;
import com.foundation.common.dict.config.DictProperties;
import com.foundation.common.dict.infrastructure.DictItemRepository;
import com.foundation.common.dict.infrastructure.DictTypeRepository;
import com.foundation.common.dict.infrastructure.MybatisDictItemRepository;
import com.foundation.common.dict.infrastructure.MybatisDictTypeRepository;
import com.foundation.common.dict.infrastructure.mapper.DictItemMapper;
import com.foundation.common.dict.infrastructure.mapper.DictTypeMapper;
import com.foundation.common.param.application.ParamService;
import com.foundation.common.param.config.ParamProperties;
import com.foundation.common.param.infrastructure.AesGcmParamCipher;
import com.foundation.common.param.infrastructure.MybatisParamHistoryRepository;
import com.foundation.common.param.infrastructure.MybatisParamRepository;
import com.foundation.common.param.infrastructure.ParamCipher;
import com.foundation.common.param.infrastructure.ParamHistoryRepository;
import com.foundation.common.param.infrastructure.ParamRepository;
import com.foundation.common.param.infrastructure.mapper.ParamHistoryMapper;
import com.foundation.common.param.infrastructure.mapper.ParamMapper;
import com.foundation.starter.mq.outbox.MybatisOutbox;
import com.foundation.starter.mq.outbox.MybatisPlusOutboxAppender;
import com.foundation.starter.mq.outbox.OutboxMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 公共域（字典与参数）自动装配（F-02 BAS-09）。
 *
 * <p>需要 {@link SqlSessionFactory}（持久化，48-CS §4 ORM 仅 MyBatis-Plus）：未启用数据源
 * （如纯配置绑定的上下文测试）时整块跳过。
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.foundation.starter.data.config.FoundationDataAutoConfiguration")
@ConditionalOnBean(SqlSessionFactory.class)
@EnableConfigurationProperties({DictProperties.class, ParamProperties.class, EventProperties.class})
public class CommonAutoConfiguration {

    // 返回类型必须是 MybatisPlusOutboxAppender（而非 OutboxAppender）：foundation-starter-mq 自带的
    // mybatisPlusOutboxAppender Bean 上的 @ConditionalOnMissingBean 按「方法返回类型」匹配，只有返回具体类型
    // 才能把它抑制掉，保证全上下文恰好一个 OutboxAppender Bean（多个自动装配以 @ConditionalOnBean(OutboxAppender.class)
    // 为前提，authz/calendar 更把它当必需构造参数）。
    @Bean
    @ConditionalOnMissingBean
    MybatisPlusOutboxAppender outboxAppender(OutboxMapper outboxMapper, ObjectMapper objectMapper) {
        return new MybatisPlusOutboxAppender(new MybatisOutbox(outboxMapper, objectMapper));
    }

    @Bean
    @ConditionalOnMissingBean
    DictTypeRepository dictTypeRepository(DictTypeMapper mapper) {
        return new MybatisDictTypeRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DictItemRepository dictItemRepository(DictItemMapper mapper, ObjectMapper objectMapper) {
        return new MybatisDictItemRepository(mapper, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DictApi dictApi(DictTypeRepository dictTypeRepository, DictItemRepository dictItemRepository,
                    OutboxAppender outboxAppender, EventProperties eventProperties,
                    DictProperties dictProperties) {
        return new DictService(dictTypeRepository, dictItemRepository, outboxAppender,
                eventProperties, dictProperties);
    }

    @Bean
    @ConditionalOnMissingBean
    ParamRepository paramRepository(ParamMapper mapper) {
        return new MybatisParamRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ParamHistoryRepository paramHistoryRepository(ParamHistoryMapper mapper) {
        return new MybatisParamHistoryRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    ParamCipher paramCipher(ParamProperties paramProperties) {
        return new AesGcmParamCipher(paramProperties.getMasterKey());
    }

    @Bean
    @ConditionalOnMissingBean
    ParamApi paramApi(ParamRepository paramRepository, ParamHistoryRepository paramHistoryRepository,
                      ParamCipher paramCipher, OutboxAppender outboxAppender,
                      EventProperties eventProperties, ParamProperties paramProperties) {
        return new ParamService(paramRepository, paramHistoryRepository, paramCipher, outboxAppender,
                eventProperties, paramProperties);
    }
}
