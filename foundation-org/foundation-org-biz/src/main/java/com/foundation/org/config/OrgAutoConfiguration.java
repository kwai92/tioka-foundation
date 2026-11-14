package com.foundation.org.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.api.event.OutboxAppender;
import com.foundation.org.api.OrgApi;
import com.foundation.org.api.PersonApi;
import com.foundation.org.api.PersonLifecycleApi;
import com.foundation.org.application.EmploymentService;
import com.foundation.org.application.JobGradeService;
import com.foundation.org.application.OrgApiAdapter;
import com.foundation.org.application.OrgExportService;
import com.foundation.org.application.OrgImportService;
import com.foundation.org.application.OrgUnitService;
import com.foundation.org.application.PersonApiAdapter;
import com.foundation.org.application.PersonLifecycleApiAdapter;
import com.foundation.org.application.PersonService;
import com.foundation.org.application.PositionService;
import com.foundation.org.domain.DigitalEmployeeRule;
import com.foundation.org.domain.EmploymentRule;
import com.foundation.org.domain.OrgTreePolicy;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.JobGradeRepository;
import com.foundation.org.infrastructure.MybatisEmploymentRepository;
import com.foundation.org.infrastructure.MybatisJobGradeRepository;
import com.foundation.org.infrastructure.MybatisOrgUnitRepository;
import com.foundation.org.infrastructure.MybatisPersonRepository;
import com.foundation.org.infrastructure.MybatisPositionRepository;
import com.foundation.org.infrastructure.MybatisReportLineRepository;
import com.foundation.org.infrastructure.OrgChangeLogWriter;
import com.foundation.org.infrastructure.OrgEventOutbox;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
import com.foundation.org.infrastructure.ReportLineRepository;
import com.foundation.org.infrastructure.mapper.EmploymentMapper;
import com.foundation.org.infrastructure.mapper.JobGradeMapper;
import com.foundation.org.infrastructure.mapper.OrgChangeLogMapper;
import com.foundation.org.infrastructure.mapper.OrgUnitMapper;
import com.foundation.org.infrastructure.mapper.PersonMapper;
import com.foundation.org.infrastructure.mapper.PositionMapper;
import com.foundation.org.infrastructure.mapper.ReportLineMapper;
import com.foundation.org.infrastructure.person.PersonCipher;
import com.foundation.org.infrastructure.person.PersonMasker;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 组织与人员自动装配（F-03 BAS-02）。
 *
 * <p>装配链路：Mapper（由 {@code foundation-starter-data} 的 {@code @MapperScan} 登记）
 * → Repository（MyBatis-Plus，48-CS §4）→ 领域规则 → 应用服务 → {@code -api} 只读契约 Bean。
 *
 * <p>依赖 {@link SqlSessionFactory}（持久化）与 {@link OutboxAppender}/{@link EventProperties}
 * （事件 outbox）；缺失时整块跳过，纯配置上下文不受影响。
 */
@AutoConfiguration
@AutoConfigureAfter(name = {
        "com.foundation.starter.data.config.FoundationDataAutoConfiguration",
        "com.foundation.common.config.CommonAutoConfiguration"})
@ConditionalOnBean({SqlSessionFactory.class, OutboxAppender.class, EventProperties.class})
@EnableConfigurationProperties({OrgProperties.class, OrgImportProperties.class,
        OrgExportProperties.class, OrgPersonProperties.class})
public class OrgAutoConfiguration {

    // ---------- 配置属性 ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgTreePolicy orgTreePolicy(OrgProperties properties) {
        return new OrgTreePolicy(properties.getMaxTreeDepth());
    }

    @Bean
    @ConditionalOnMissingBean
    public EmploymentRule employmentRule() {
        return new EmploymentRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public DigitalEmployeeRule digitalEmployeeRule() {
        return new DigitalEmployeeRule();
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonCipher personCipher(OrgPersonProperties properties) {
        return new PersonCipher(properties.getMasterKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonMasker personMasker(OrgPersonProperties properties) {
        return new PersonMasker(properties.getMask().getMobile(), properties.getMask().getEmail());
    }

    // ---------- 仓储（MyBatis-Plus） ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgUnitRepository orgUnitRepository(OrgUnitMapper mapper) {
        return new MybatisOrgUnitRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PositionRepository positionRepository(PositionMapper mapper) {
        return new MybatisPositionRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobGradeRepository jobGradeRepository(JobGradeMapper mapper) {
        return new MybatisJobGradeRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonRepository personRepository(PersonMapper mapper) {
        return new MybatisPersonRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmploymentRepository employmentRepository(EmploymentMapper mapper) {
        return new MybatisEmploymentRepository(mapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public ReportLineRepository reportLineRepository(ReportLineMapper mapper) {
        return new MybatisReportLineRepository(mapper);
    }

    // ---------- 变更历史与事件 ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgChangeLogWriter orgChangeLogWriter(OrgChangeLogMapper mapper, ObjectMapper json) {
        return new OrgChangeLogWriter(mapper, json);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgEventOutbox orgEventOutbox(OutboxAppender outbox, EventProperties eventProperties) {
        return new OrgEventOutbox(outbox, eventProperties);
    }

    // ---------- 应用服务 ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgUnitService orgUnitService(OrgUnitRepository orgUnits, PositionRepository positions,
                                         EmploymentRepository employments, OrgTreePolicy treePolicy,
                                         OrgChangeLogWriter changeLog, OrgEventOutbox events) {
        return new OrgUnitService(orgUnits, positions, employments, treePolicy, changeLog, events);
    }

    @Bean
    @ConditionalOnMissingBean
    public PositionService positionService(PositionRepository positions, OrgUnitRepository orgUnits,
                                           OrgChangeLogWriter changeLog) {
        return new PositionService(positions, orgUnits, changeLog);
    }

    @Bean
    @ConditionalOnMissingBean
    public JobGradeService jobGradeService(JobGradeRepository jobGrades, OrgChangeLogWriter changeLog) {
        return new JobGradeService(jobGrades, changeLog);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonService personService(PersonRepository persons, EmploymentRepository employments,
                                       ReportLineRepository reportLines, OrgUnitRepository orgUnits,
                                       PositionRepository positions, PersonCipher cipher, PersonMasker masker,
                                       EmploymentRule employmentRule, DigitalEmployeeRule digitalEmployeeRule,
                                       OrgChangeLogWriter changeLog, OrgEventOutbox events) {
        return new PersonService(persons, employments, reportLines, orgUnits, positions, cipher, masker,
                employmentRule, digitalEmployeeRule, changeLog, events);
    }

    @Bean
    @ConditionalOnMissingBean
    public EmploymentService employmentService(EmploymentRepository employments, PersonRepository persons,
                                               OrgUnitRepository orgUnits, PositionRepository positions,
                                               EmploymentRule rule, OrgChangeLogWriter changeLog,
                                               OrgEventOutbox events) {
        return new EmploymentService(employments, persons, orgUnits, positions, rule, changeLog, events);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgImportService orgImportService(OrgUnitRepository orgUnits, PositionRepository positions,
                                             PersonRepository persons, EmploymentRepository employments,
                                             PersonCipher cipher, OrgTreePolicy treePolicy,
                                             EmploymentRule employmentRule, OrgChangeLogWriter changeLog,
                                             OrgImportProperties properties) {
        return new OrgImportService(orgUnits, positions, persons, employments, cipher, treePolicy,
                employmentRule, changeLog, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OrgExportService orgExportService(OrgUnitRepository orgUnits, PersonRepository persons,
                                             EmploymentRepository employments, PositionRepository positions,
                                             PersonCipher cipher, PersonMasker masker,
                                             OrgExportProperties properties) {
        return new OrgExportService(orgUnits, persons, employments, positions, cipher, masker, properties);
    }

    // ---------- 只读契约（其他模块依赖） ----------

    @Bean
    @ConditionalOnMissingBean
    public OrgApi orgApi(OrgUnitRepository orgUnits) {
        return new OrgApiAdapter(orgUnits);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonApi personApi(PersonRepository persons, EmploymentRepository employments,
                               ReportLineRepository reportLines, OrgUnitRepository orgUnits,
                               PersonCipher cipher) {
        return new PersonApiAdapter(persons, employments, reportLines, orgUnits, cipher);
    }

    @Bean
    @ConditionalOnMissingBean
    public PersonLifecycleApi personLifecycleApi(PersonService persons) {
        return new PersonLifecycleApiAdapter(persons);
    }
}
