package com.foundation.server.assembly;

import com.foundation.authz.domain.SubjectMembership;
import com.foundation.org.domain.Employment;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OrgSubjectMembership} 的口径单测（离线，不依赖数据库/中间件）。
 *
 * <p>本类守的是**语义**而非连通性：口径一旦写错，生产后果是「权限多给或少给」，
 * 属不可逆的安全问题，故每条口径都有对应断言。真库往返由 gate 闸门覆盖。
 *
 * <p>刻意覆盖几个容易被忽略的边界：
 * <ul>
 *   <li>{@code position_id} 列可空 —— 无岗位的任职不得产生「空岗位归属」，
 *       否则 {@code EffectiveRoleResolver.collectByPost} 的
 *       {@code personPosts.contains(grant.subjectId())} 会与空 subjectId 意外匹配；</li>
 *   <li>{@code descendantsOf} 必须**含自身** —— 接口 javadoc 与被替换的占位实现
 *       （{@code Set.of(orgUnitId)}）都是这个口径，{@code DataScopeResolver.expandDescendants}
 *       与 {@code includeDescendants} 分支都依赖它，漏了自身会让「本部门及下级」档少掉本部门；</li>
 *   <li>入参为空/空白时返回空集合，而不是把 {@code null} 传进仓储。</li>
 * </ul>
 */
class OrgSubjectMembershipTest {

    private static final String PERSON = "per_1";
    private static final String ORG_ROOT = "org_root";
    private static final String ORG_CHILD = "org_child";
    private static final String POSITION = "pos_1";

    private final EmploymentRepository employments = mock(EmploymentRepository.class);
    private final OrgUnitRepository orgUnits = mock(OrgUnitRepository.class);
    private final OrgSubjectMembership membership = new OrgSubjectMembership(employments, orgUnits);

    @Test
    void 构造时缺少任一仓储立即失败() {
        assertThatThrownBy(() -> new OrgSubjectMembership(null, orgUnits))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EmploymentRepository");
        assertThatThrownBy(() -> new OrgSubjectMembership(employments, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OrgUnitRepository");
    }

    @Test
    void 组织归属取全部未结束任职的组织单元_主任职与兼职都算() {
        when(employments.openByPerson(PERSON)).thenReturn(List.of(
                employment(ORG_ROOT, POSITION, true),
                employment(ORG_CHILD, null, false)));

        assertThat(membership.orgUnitsOf(PERSON)).containsExactlyInAnyOrder(ORG_ROOT, ORG_CHILD);
    }

    @Test
    void 同一组织单元的多条任职去重() {
        when(employments.openByPerson(PERSON)).thenReturn(List.of(
                employment(ORG_ROOT, POSITION, true),
                employment(ORG_ROOT, null, false)));

        assertThat(membership.orgUnitsOf(PERSON)).containsExactly(ORG_ROOT);
    }

    @Test
    void 组织归属忽略空白的组织单元ID() {
        when(employments.openByPerson(PERSON)).thenReturn(List.of(
                employment(ORG_ROOT, POSITION, true),
                employment("", null, false),
                employment(null, null, false)));

        assertThat(membership.orgUnitsOf(PERSON)).containsExactly(ORG_ROOT);
    }

    @Test
    void 岗位归属只取未结束任职的岗位且过滤空岗位() {
        // position_id 可空：第二条任职无岗位，不得产生空字符串/null 归属
        when(employments.openByPerson(PERSON)).thenReturn(List.of(
                employment(ORG_ROOT, POSITION, true),
                employment(ORG_CHILD, null, false),
                employment(ORG_CHILD, "  ", false)));

        assertThat(membership.postsOf(PERSON)).containsExactly(POSITION);
    }

    @Test
    void 无任职的人员两条归属均为空集() {
        when(employments.openByPerson(PERSON)).thenReturn(List.of());

        assertThat(membership.orgUnitsOf(PERSON)).isEmpty();
        assertThat(membership.postsOf(PERSON)).isEmpty();
    }

    @Test
    void 人员ID为空时不查库直接返回空集() {
        assertThat(membership.orgUnitsOf(null)).isEmpty();
        assertThat(membership.orgUnitsOf("  ")).isEmpty();
        assertThat(membership.postsOf(null)).isEmpty();
        assertThat(membership.postsOf("")).isEmpty();

        // 未发生任何仓储调用（避免用 null/空白参数打库）
        org.mockito.Mockito.verifyNoInteractions(employments);
    }

    @Test
    void 子树查询直接复用仓储的含自身语义() {
        // descendantIds 的契约是「自身 + 全部下级」；本实现不得自行增删，否则与
        // DataScopeResolver / EffectiveRoleResolver 的 includeDescendants 口径不一致
        when(orgUnits.descendantIds(ORG_ROOT)).thenReturn(Set.of(ORG_ROOT, ORG_CHILD));

        assertThat(membership.descendantsOf(ORG_ROOT)).containsExactlyInAnyOrder(ORG_ROOT, ORG_CHILD);
    }

    @Test
    void 组织单元ID为空时子树为空集() {
        assertThat(membership.descendantsOf(null)).isEmpty();
        assertThat(membership.descendantsOf("")).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(orgUnits);
    }

    @Test
    void 受影响人员按组织单元与岗位反查() {
        when(employments.openPersonIdsInOrgUnits(anyCollection())).thenReturn(Set.of("per_a", "per_b"));
        when(employments.openPersonIdsInPosition(POSITION)).thenReturn(Set.of("per_c"));

        assertThat(membership.personsInOrgUnit(ORG_ROOT)).containsExactlyInAnyOrder("per_a", "per_b");
        assertThat(membership.personsInPost(POSITION)).containsExactly("per_c");
    }

    @Test
    void 组织单元ID为空时受影响人员为空集() {
        assertThat(membership.personsInOrgUnit(null)).isEmpty();
        assertThat(membership.personsInOrgUnit("  ")).isEmpty();

        // 岗位侧的空入参守卫在仓储实现里（MybatisEmploymentRepository.openPersonIdsInPosition），
        // 端口实现只做转发，故此处只断言不额外拦截
        when(employments.openPersonIdsInPosition(anyString())).thenReturn(Set.of());
        assertThat(membership.personsInPost("")).isEmpty();
    }

    // ---------------------------------------------------------------- 装配
    //
    // 用 AutoConfigurations.of(...) 而非 withUserConfiguration 加载被测类：它已声明为
    // @AutoConfiguration（必须晚于 OrgAutoConfiguration、早于 AuthzAutoConfiguration），
    // 以用户配置加载会丢掉这个排序语义，测的就不是生产路径。
    //
    // 装配条件已从「域属性开关」改为「仓储 Bean 是否存在」——因为域属性为 true 并不保证仓储存在
    // （org 域自身还受 @ConditionalOnBean(SqlSessionFactory.class) 约束）。故下面测的是真实条件：
    // 仓储在 → 装配真库实现；仓储不在（如 minimal profile、或上下文排除了数据源）→ 静默跳过而非启动失败。

    @Test
    void 仓储齐备时装配真库实现() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FoundationServerAssemblyConfiguration.class))
                .withUserConfiguration(RepoConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SubjectMembership.class);
                    assertThat(context.getBean(SubjectMembership.class))
                            .isInstanceOf(OrgSubjectMembership.class);
                });
    }

    @Test
    void 仓储缺失时静默跳过而非启动失败() {
        // 复现 FoundationContextTest 的场景：上下文排除数据源后 org 域整块不装配，
        // 此时域属性仍是默认 true，但 EmploymentRepository 不存在。
        // 首版用域属性做条件，导致本场景下上下文启动失败（4 个用例报错）；改为按仓储存在性判断后应静默跳过。
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FoundationServerAssemblyConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SubjectMembership.class);
                });
    }

    @Test
    void 只提供其中一个仓储时也不装配() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FoundationServerAssemblyConfiguration.class))
                .withUserConfiguration(EmploymentRepoOnlyConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SubjectMembership.class);
                });
    }

    @Test
    void 接入方自行注册时不覆盖() {
        // @ConditionalOnMissingBean 的意义：接入方（如 Tioka）若自带 SubjectMembership，平台不得顶掉
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FoundationServerAssemblyConfiguration.class))
                .withUserConfiguration(RepoConfig.class, CustomMembershipConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SubjectMembership.class);
                    assertThat(context.getBean(SubjectMembership.class))
                            .as("应保留接入方自己的实现")
                            .isNotInstanceOf(OrgSubjectMembership.class);
                });
    }

    @Test
    void 装配类已登记进AutoConfigurationimports() {
        // 上面几个用例用 AutoConfigurations.of(...) 显式加载配置类，**绕过了** imports 文件；
        // 若 META-INF/spring/....AutoConfiguration.imports 路径或 FQCN 写错，生产环境会静默不装配
        // （退回 NoopSubjectMembership 占位实现）而所有测试照样绿——典型的半接线假绿。
        // 故用 Spring Boot 加载 imports 的真实机制（ImportCandidates）显式校验登记生效。
        List<String> candidates = ImportCandidates
                .load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        assertThat(candidates)
                .as("装配类必须登记在 META-INF/spring/"
                        + "org.springframework.boot.autoconfigure.AutoConfiguration.imports，"
                        + "否则生产环境静默退回 Noop 占位实现")
                .contains(FoundationServerAssemblyConfiguration.class.getName());
    }

    /**
     * 提供装配所需的两个仓储（真库实现由 {@code OrgAutoConfiguration} 注册，此处用替身）。
     *
     * <p>装配条件**必须**直接测 {@link FoundationServerAssemblyConfiguration} 真实类，
     * 不能在本测试里另写一份同注解的 Configuration——复制品即使与真实类条件不一致也照样通过，
     * 那正是「测试给虚假信心」的典型形态。
     */
    @Configuration(proxyBeanMethods = false)
    static class RepoConfig {

        @Bean
        EmploymentRepository employmentRepository() {
            return mock(EmploymentRepository.class);
        }

        @Bean
        OrgUnitRepository orgUnitRepository() {
            return mock(OrgUnitRepository.class);
        }
    }

    /** 只提供任职仓储，用于验证「两个仓储缺一不可」。 */
    @Configuration(proxyBeanMethods = false)
    static class EmploymentRepoOnlyConfig {

        @Bean
        EmploymentRepository employmentRepository() {
            return mock(EmploymentRepository.class);
        }
    }

    /** 模拟接入方自带实现。 */
    @Configuration(proxyBeanMethods = false)
    static class CustomMembershipConfig {

        @Bean
        SubjectMembership customSubjectMembership() {
            return new SubjectMembership() {
                @Override
                public Set<String> orgUnitsOf(String personId) {
                    return Set.of();
                }

                @Override
                public Set<String> postsOf(String personId) {
                    return Set.of();
                }

                @Override
                public Set<String> descendantsOf(String orgUnitId) {
                    return Set.of();
                }
            };
        }
    }

    // ---------------------------------------------------------------- 造数

    private static Employment employment(String orgUnitId, String positionId, boolean primary) {
        return new Employment("emp_" + orgUnitId + "_" + positionId + "_" + primary,
                PERSON, orgUnitId, positionId, primary, LocalDate.of(2026, 1, 1), null);
    }
}
