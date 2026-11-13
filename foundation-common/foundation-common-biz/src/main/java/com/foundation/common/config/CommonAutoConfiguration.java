package com.foundation.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.dict.DictApi;
import com.foundation.common.api.param.ParamApi;
import com.foundation.common.dict.application.DictService;
import com.foundation.common.dict.config.DictProperties;
import com.foundation.common.dict.infrastructure.DictItemRepository;
import com.foundation.common.dict.infrastructure.DictTypeRepository;
import com.foundation.common.dict.infrastructure.JdbcDictItemRepository;
import com.foundation.common.dict.infrastructure.JdbcDictTypeRepository;
import com.foundation.common.outbox.EventProperties;
import com.foundation.common.outbox.JdbcOutboxAppender;
import com.foundation.common.outbox.OutboxAppender;
import com.foundation.common.param.application.ParamService;
import com.foundation.common.param.config.ParamProperties;
import com.foundation.common.param.infrastructure.AesGcmParamCipher;
import com.foundation.common.param.infrastructure.JdbcParamHistoryRepository;
import com.foundation.common.param.infrastructure.JdbcParamRepository;
import com.foundation.common.param.infrastructure.ParamCipher;
import com.foundation.common.param.infrastructure.ParamHistoryRepository;
import com.foundation.common.param.infrastructure.ParamRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 公共域（字典与参数）自动装配（F-02 BAS-09）。
 *
 * <p>需要 {@link JdbcTemplate}：未启用数据源（如纯配置绑定的上下文测试）时整块跳过。
 * BAS-24 抽出 {@code foundation-starter-data} 后，本类改为只做域内装配。
 */
@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@ConditionalOnBean(JdbcTemplate.class)
@EnableConfigurationProperties({DictProperties.class, ParamProperties.class, EventProperties.class})
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxAppender outboxAppender(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcOutboxAppender(jdbcTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    DictTypeRepository dictTypeRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDictTypeRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    DictItemRepository dictItemRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcDictItemRepository(jdbcTemplate, objectMapper);
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
    ParamRepository paramRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcParamRepository(jdbcTemplate);
    }

    @Bean
    @ConditionalOnMissingBean
    ParamHistoryRepository paramHistoryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcParamHistoryRepository(jdbcTemplate);
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
