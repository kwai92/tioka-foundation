package com.foundation.server.assembly;

import com.foundation.authz.config.AuthzAutoConfiguration;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.org.config.OrgAutoConfiguration;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 跨模块端口的装配层接线（施工总纲 §2.1「{@code foundation-server} 只做装配」）。
 *
 * <p>本类存在的唯一理由：有些端口声明在某个 {@code *-biz} 的 {@code domain} 包，而实现所需的仓储在
 * 另一个 {@code *-biz}。跨模块只能依赖对方的 {@code *-api}（ArchUnit 规则 {@code modulesDependOnlyOnApi}），
 * 故这类接线**只能**落在同时依赖两个 biz 模块的 server 装配层。
 *
 * <p><b>为什么必须是自动配置而不是普通 {@code @Configuration}</b>：本 Bean 依赖 org 域自动配置产出的仓储，
 * 且必须赶在 authz 域注册占位实现**之前**。普通 {@code @Configuration} 由组件扫描在自动配置之前处理，
 * 那时 {@code @ConditionalOnBean} 看不到任何自动配置产出的 Bean（会恒判为「不存在」而静默跳过）。
 * 因此本类经 {@code META-INF/spring/....AutoConfiguration.imports} 注册，并用 after/before 显式钉住顺序：
 * <pre>OrgAutoConfiguration → 本类 → AuthzAutoConfiguration</pre>
 *
 * <p><b>为什么用 {@code @ConditionalOnBean} 而不是域属性开关</b>：域属性为 true 并**不保证**仓储存在——
 * org 域自身还受 {@code @ConditionalOnBean(SqlSessionFactory.class)} 约束。实证：
 * {@code FoundationContextTest} 排除了 {@code DataSourceAutoConfiguration}，此时域属性仍是默认的 true，
 * 但 {@code OrgAutoConfiguration} 整块不装配；若只判属性，本 Bean 会因缺 {@code EmploymentRepository}
 * 直接让上下文启动失败（本类首版即因此使该测试 4 个用例报错）。故以「仓储真的在不在」为条件，
 * 属性判断反而是多余的第二套语义。
 */
@AutoConfiguration(after = OrgAutoConfiguration.class, before = AuthzAutoConfiguration.class)
public class FoundationServerAssemblyConfiguration {

    /**
     * 用真库的组织归属查询替代 authz 域的占位实现 {@code NoopSubjectMembership}。
     *
     * <p>顺序是生效的关键：本 Bean 必须先于 {@code AuthzAutoConfiguration} 注册，否则后者以
     * {@code @ConditionalOnMissingBean} 注册的 {@code NoopSubjectMembership} 会先占位，
     * 使本实现被静默跳过（表现为「配了但没生效」的半接线）。
     *
     * <p>再加 {@code @ConditionalOnMissingBean} 是为了让接入方仍可继续覆盖本实现。
     */
    @Bean
    @ConditionalOnBean({EmploymentRepository.class, OrgUnitRepository.class})
    @ConditionalOnMissingBean(SubjectMembership.class)
    public SubjectMembership orgSubjectMembership(EmploymentRepository employments,
                                                 OrgUnitRepository orgUnits) {
        return new OrgSubjectMembership(employments, orgUnits);
    }
}
