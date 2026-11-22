package com.foundation.server.gate;

import com.foundation.authz.api.DataScopeApi;
import com.foundation.authz.api.DataScopeView;
import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.RoleCmd;
import com.foundation.authz.api.RoleDataScopeCmd;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.ScopeView;
import com.foundation.authz.domain.DeptScope;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.OrgStatus;
import com.foundation.org.api.dto.OrgUnitType;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.Employment;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.OrgTreePolicy;
import com.foundation.org.domain.OrgUnit;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.server.assembly.OrgSubjectMembership;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1，BAS-06 / W83）：数据范围**部门五档**在真实库上的解析正确性。
 *
 * <p>验收口径（F-00 §6.1）：华东区负责人只拿到华东区取值；部门五档各一例。
 *
 * <p><b>与既有单测的分工</b>：{@code DataScopeAppServiceTest} 用 {@code TestDoubles.StubSubjectMembership}
 * （内存 Map）验的是 {@code DataScopeResolver} 的合并算法（跨角色并集、ALL 哨兵、维度累积、MANUAL 不可覆盖）；
 * 本闸门跑完整 {@code FoundationApplication} + PostgreSQL 容器，验的是**算法之下那层单测碰不到的东西**——
 * {@code DEPT} / {@code DEPT_AND_SUB} 两档必须经 {@code SubjectMembership} 端口读**真库组织树**才能展开：
 * 物化路径前缀匹配（{@code descendantIds} 用 {@code likeRight(path, self.path + "/")}）是否真能命中子树、
 * {@code openByPerson} 的 {@code end_date IS NULL} 过滤在真实 SQL 上是否生效、
 * {@code fdn_role_data_scope} / {@code fdn_role_grant} 的 MyBatis 映射与真表 DDL 是否吻合。
 *
 * <p><b>为什么必须显式断言装配实现</b>：{@code AuthzAutoConfiguration} 以 {@code @ConditionalOnMissingBean}
 * 注册占位实现 {@code NoopSubjectMembership}（{@code orgUnitsOf} 恒返回空集）。若
 * {@code FoundationServerAssemblyConfiguration} 因故未生效，{@code DEPT} 档会静默退化为空集合，
 * 而「未断言任何取值」的弱断言可能以「空 == 空」的形态假绿。故 {@link #assemblyUsesRealMembershipNotPlaceholder()}
 * 把装配实现本身做成一条可自动判定的信号。
 *
 * <p><b>为什么每档一个独立人员</b>：{@code DataScopeResolver.resolveDept} 跨角色取并集，且 {@code ALL}
 * 是哨兵（{@code all ? ofAll() : of(units)}）会抹掉其余档位的结果。若一人身兼五档，只能观测到 ALL，
 * 无法分别断言。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest
class GfDataScopeGateIT {

    /** 组织树：集团 → {华东区 → 华东销售部, 华北区}。 */
    private static final String ORG_ROOT = "GATE_ROOT";
    private static final String ORG_EAST = "GATE_EAST";
    private static final String ORG_EAST_SALES = "GATE_EAST_SALES";
    private static final String ORG_NORTH = "GATE_NORTH";

    /** appCode 传 null：{@code AppIdResolver.resolve(null)} 返回 empty，
     * 而 {@code EffectiveRoleResolver.availableIn} 为 {@code appId.isEmpty() || ...}，
     * 故角色恒可用——本闸门聚焦部门档位，无需造 app 域数据。 */
    private static final String NO_APP = null;

    private static final String OPERATOR = "gate";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private DataScopeApi dataScopes;

    @Autowired
    private RoleApi roles;

    @Autowired
    private GrantApi grants;

    /** 被测端口的实际装配实现，用于挡住占位实现静默生效。 */
    @Autowired
    private SubjectMembership membership;

    @Autowired
    private OrgUnitRepository orgUnits;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private EmploymentRepository employments;

    /** 物化路径规则由平台自己给出，避免测试手拼路径与生产口径漂移。 */
    @Autowired
    private OrgTreePolicy treePolicy;

    @Autowired
    private JdbcTemplate jdbc;

    private String rootId;
    private String eastId;
    private String eastSalesId;
    private String northId;

    @BeforeEach
    void cleanUp() {
        // 逆外键依赖顺序清理：引用者先于被引用者。
        // org 域清单与 GfOrgImportGateIT 一致（fdn_external_identity 属 account 域但 FK 引用 fdn_person）。
        //
        // **只 TRUNCATE 无迁移种子的表**：fdn_role / fdn_role_mutex / fdn_role_permission 在
        // V4__authz_center.sql 里种入了三员角色（system_admin / security_admin / audit_admin）、
        // 三员互斥对与内置角色的 fdn:* 权限码。TRUNCATE 会永久清掉这些种子——而 gate job 内所有
        // 闸门共享同一个 PostgreSQL 容器（FoundationContainers.PG 是 static 单例），清掉后
        // 同批次的 GfPermissionGateIT 等依赖种子的闸门必然失败。故对这三张表只删除本测试造的
        // gate.role.* 行（先删引用它们的子表行，再删角色本身）。
        jdbc.execute("TRUNCATE fdn_person_dimension, fdn_role_dimension_value, fdn_role_data_scope, "
                + "fdn_role_grant, fdn_dimension_value, fdn_data_dimension, fdn_authz_snapshot, "
                + "fdn_employment, fdn_report_line, fdn_external_identity, fdn_position, "
                + "fdn_org_change_log, fdn_job_grade, fdn_org_unit, fdn_person");
        jdbc.update("DELETE FROM fdn_role_permission WHERE role_code LIKE 'gate.role.%'");
        jdbc.update("DELETE FROM fdn_role_mutex WHERE role_code LIKE 'gate.role.%' "
                + "OR mutex_role_code LIKE 'gate.role.%'");
        jdbc.update("DELETE FROM fdn_role WHERE code LIKE 'gate.role.%'");
    }

    /**
     * 清理不得破坏迁移种子（V4__authz_center.sql 种入的三员角色、互斥对与内置权限码）。
     *
     * <p>{@code cleanUp()} 是 {@code @BeforeEach}，已在本用例前执行过，故此处直接断言种子仍在。
     * 这条断言把「gate job 内闸门共享同一 PG 容器（{@code FoundationContainers.PG} 是 static 单例，
     * surefire 默认 forkCount=1 / reuseForks=true → 同一 JVM、Flyway 只跑一次、种子只种一次）」这一
     * 隐含约束变成可自动判定的信号：若有人把清理改回 TRUNCATE 那三张有种子的表，本用例会红，
     * 而不是等到同批次的其它闸门（如依赖互斥对种子的权限闸门）静默失败后才发现。
     */
    @Test
    void cleanUpPreservesMigrationSeedData() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_role WHERE code IN ('system_admin','security_admin','audit_admin')",
                Integer.class))
                .as("三员角色种子必须在清理后仍在")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_role_mutex", Integer.class))
                .as("三员互斥对种子（3 对）必须在清理后仍在")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_app WHERE code = 'foundation'", Integer.class))
                .as("平台应用种子必须在清理后仍在")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT role_code) FROM fdn_role_permission WHERE role_code IN "
                        + "('system_admin','security_admin','audit_admin','org_admin','app_admin')",
                Integer.class))
                .as("5 个内置角色的 fdn:* 权限码种子必须在清理后仍在")
                .isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_role_permission "
                        + "WHERE role_code = 'system_admin' AND code = 'fdn:manifest:import'",
                Integer.class))
                .as("system_admin 的清单导入权限码是权限闸门前置，必须仍在")
                .isEqualTo(1);
    }

    @Test
    void assemblyUsesRealMembershipNotPlaceholder() {
        // 这是 DEPT / DEPT_AND_SUB 两档能成立的前提。若装配静默退回 NoopSubjectMembership，
        // 后续用例的部门集合会全空，弱断言可能假绿，故把装配实现本身做成独立信号。
        assertThat(membership)
                .as("SubjectMembership 必须装配真库实现而非占位实现（NoopSubjectMembership 的 orgUnitsOf 恒空）")
                .isInstanceOf(OrgSubjectMembership.class);

        seedOrgTree();
        String personId = seedPerson("A0001");
        seedEmployment(personId, eastId, true, null);

        assertThat(membership.orgUnitsOf(personId))
                .as("真库实现应能从 fdn_employment 读出组织归属")
                .containsExactly(eastId);
        assertThat(membership.descendantsOf(eastId))
                .as("子树应含自身与下级（物化路径前缀匹配）")
                .containsExactlyInAnyOrder(eastId, eastSalesId)
                .doesNotContain(rootId, northId);
    }

    @Test
    void fiveDeptScopesEachResolveAsSpecifiedOnRealDatabase() {
        seedOrgTree();

        // ALL：哨兵，不限部门
        String allPerson = seedScopedPerson("B0001", "gate.role.all", DeptScope.ALL, List.of(), eastId);
        // CUSTOM：显式指定的两个组织单元（一个下级部门 + 一个兄弟区域，验证不靠组织归属推导）
        String customPerson = seedScopedPerson("B0002", "gate.role.custom", DeptScope.CUSTOM,
                List.of(eastSalesId, northId), eastId);
        // DEPT：华东区负责人，只应拿到华东区（F-00 §6.1 验收口径）
        String deptPerson = seedScopedPerson("B0003", "gate.role.dept", DeptScope.DEPT, List.of(), eastId);
        // DEPT_AND_SUB：同样任职华东区，但应展开出华东销售部
        String deptSubPerson = seedScopedPerson("B0004", "gate.role.dept_sub", DeptScope.DEPT_AND_SUB,
                List.of(), eastId);
        // SELF：部门集合为空且非哨兵
        String selfPerson = seedScopedPerson("B0005", "gate.role.self", DeptScope.SELF, List.of(), eastId);

        // 华东区负责人由本用例的 DEPT 档人员担任，坐实「华东区负责人只拿到华东区」这一验收语义
        setLeader(eastId, deptPerson);

        ScopeView all = resolveDept(allPerson);
        assertThat(all.all()).as("ALL 档应为哨兵 all=true").isTrue();
        assertThat(all.values()).as("ALL 档不枚举部门").isEmpty();

        ScopeView custom = resolveDept(customPerson);
        assertThat(custom.all()).as("CUSTOM 档不是哨兵").isFalse();
        assertThat(custom.values())
                .as("CUSTOM 档应恰为指定的两个组织单元")
                .containsExactlyInAnyOrder(eastSalesId, northId);

        ScopeView dept = resolveDept(deptPerson);
        assertThat(dept.all()).as("DEPT 档不是哨兵").isFalse();
        assertThat(dept.values())
                .as("华东区负责人的 DEPT 档只应拿到华东区")
                .containsExactly(eastId);
        assertThat(dept.values())
                .as("DEPT 档不得下探子部门，也不得上溯集团或旁及兄弟区域")
                .doesNotContain(eastSalesId, rootId, northId);

        ScopeView deptAndSub = resolveDept(deptSubPerson);
        assertThat(deptAndSub.all()).as("DEPT_AND_SUB 档不是哨兵").isFalse();
        assertThat(deptAndSub.values())
                .as("DEPT_AND_SUB 档应为华东区自身加其下级部门")
                .containsExactlyInAnyOrder(eastId, eastSalesId);
        assertThat(deptAndSub.values())
                .as("DEPT_AND_SUB 档不得越界到上级或兄弟区域")
                .doesNotContain(rootId, northId);

        // 同一部门任职、不同档位得到不同结果，证明差异来自档位而非组织归属
        assertThat(dept.values())
                .as("DEPT 与 DEPT_AND_SUB 任职相同却结果不同")
                .isNotEqualTo(deptAndSub.values());

        ScopeView self = resolveDept(selfPerson);
        assertThat(self.all()).as("SELF 档不是哨兵（哨兵是 ALL 的语义）").isFalse();
        assertThat(self.values()).as("SELF 档部门集合为空").isEmpty();

        // 验收口径是「部门五档各一例」，故把完整性做成可自动判定的信号：
        // 若 DeptScope 将来新增档位而本闸门未补测，这里会红，不依赖人记得去改测试。
        assertThat(coveredScopes())
                .as("本闸门应覆盖 DeptScope 的全部档位")
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(DeptScope.values()).map(Enum::name).toList());
    }

    @Test
    void deptScopeIgnoresClosedEmploymentOnRealDatabase() {
        seedOrgTree();
        String personId = seedScopedPerson("C0001", "gate.role.closed", DeptScope.DEPT, List.of(), northId);

        // 已结束的历史任职不应继续给出数据范围：这条过滤在 SQL 的 end_date IS NULL 上，
        // 内存 stub 无法证明真实查询确实过滤了
        seedEmployment(personId, eastSalesId, false, LocalDate.of(2026, 6, 30));

        assertThat(membership.orgUnitsOf(personId))
                .as("已结束任职不参与组织归属")
                .containsExactly(northId)
                .doesNotContain(eastSalesId);

        assertThat(resolveDept(personId).values())
                .as("DEPT 档只应拿到未结束任职所在的华北区")
                .containsExactly(northId);
    }

    @Test
    void concurrentPostExpandsEveryOpenDepartmentOnRealDatabase() {
        seedOrgTree();
        // 主任职在华东区，兼职在华北区
        String personId = seedScopedPerson("D0001", "gate.role.concurrent", DeptScope.DEPT, List.of(), eastId);
        seedEmployment(personId, northId, false, null);

        assertThat(membership.orgUnitsOf(personId))
                .as("主任职与兼职都算组织归属")
                .containsExactlyInAnyOrder(eastId, northId);

        assertThat(resolveDept(personId).values())
                .as("DEPT 档应含全部未结束任职的部门")
                .containsExactlyInAnyOrder(eastId, northId);
    }

    // ---------------------------------------------------------------- 解析

    private ScopeView resolveDept(String personId) {
        DataScopeView view = dataScopes.resolve(personId, NO_APP, Instant.now());
        return view.deptScope();
    }

    // ---------------------------------------------------------------- 造数

    /**
     * 造一个「角色 + 直接授予 + 部门档位」齐备的人员。
     *
     * <p>角色用 {@code SYSTEM} 范围且**不带任何权限码**：
     * {@code PermissionCatalog.platformScopedOnly(空集)} 为 true，故通过全局角色校验；
     * 空权限码使 {@code HighRiskGrantReviewService.deferIfHighRisk} 直接返回 false（授权真落库而非转待复核），
     * 且 {@code RoleMutexGuard} 无互斥对时直接放行。
     */
    private String seedScopedPerson(String empNo, String roleCode, DeptScope deptScope,
                                    List<String> deptUnits, String orgUnitId) {
        String personId = seedPerson(empNo);
        seedEmployment(personId, orgUnitId, true, null);

        roles.create(new RoleCmd(roleCode, roleCode, "SYSTEM", null, null, List.of(), OPERATOR));
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personId, false,
                null, null, "MANUAL", OPERATOR));
        dataScopes.setRoleDataScope(roleCode,
                new RoleDataScopeCmd(deptScope.name(), deptUnits, Map.of()));
        return personId;
    }

    /** 集团 → {华东区 → 华东销售部, 华北区}，物化路径由 {@link OrgTreePolicy} 生成。 */
    private void seedOrgTree() {
        rootId = insertOrgUnit(ORG_ROOT, "闸门集团", OrgUnitType.GROUP, null, null, 1, 0);
        eastId = insertOrgUnit(ORG_EAST, "华东区", OrgUnitType.COMPANY, rootId,
                orgUnits.findById(rootId).orElseThrow().path(), 2, 1);
        northId = insertOrgUnit(ORG_NORTH, "华北区", OrgUnitType.COMPANY, rootId,
                orgUnits.findById(rootId).orElseThrow().path(), 2, 2);
        eastSalesId = insertOrgUnit(ORG_EAST_SALES, "华东销售部", OrgUnitType.DEPARTMENT, eastId,
                orgUnits.findById(eastId).orElseThrow().path(), 3, 1);
    }

    private String insertOrgUnit(String code, String name, OrgUnitType type, String parentId,
                                 String parentPath, int depth, int sortNo) {
        String id = Ids.newId(OrgIdPrefix.ORG_UNIT);
        String path = parentPath == null ? treePolicy.rootPath(id) : treePolicy.childPath(parentPath, id);
        orgUnits.insert(new OrgUnit(id, code, name, type, parentId, path, depth, sortNo,
                null, null, OrgStatus.ACTIVE, "local", null, 0L));
        return id;
    }

    private String seedPerson(String empNo) {
        String id = Ids.newId(OrgIdPrefix.PERSON);
        // 手机 / 邮箱列均可空，本闸门不涉及敏感字段与查重
        persons.insert(new Person(id, empNo, "闸门人员-" + empNo, PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, null, null, "local", null, 0L));
        return id;
    }

    /**
     * 设置组织单元负责人。
     *
     * <p>{@code leader_person_id} 不参与数据范围解析（{@code resolveDept} 只读 {@code membership}），
     * 设置它是为了让「华东区负责人只拿到华东区」这条验收语义在数据上名副其实，
     * 而非断言负责人的解析行为。{@code OrgUnit} 是无 wither 的 record，
     * 整体重建会硬编码 code / path / depth 等与被测无关的字段、埋下与造数逻辑漂移的隐患，
     * 故这里只精确更新目标列。
     */
    private void setLeader(String orgUnitId, String personId) {
        int updated = jdbc.update("UPDATE fdn_org_unit SET leader_person_id = ? WHERE id = ?",
                personId, orgUnitId);
        assertThat(updated).as("负责人应写入组织单元 %s", orgUnitId).isEqualTo(1);
    }

    /** 任职起始日：需早于任何 endDate，因 fdn_employment 有 {@code ck_employment_range}（end_date >= start_date）。 */
    private static final LocalDate EMPLOYMENT_START = LocalDate.of(2026, 1, 1);

    /**
     * @param endDate 非 null 表示已结束任职；{@code primary=false} 表示兼职
     *                （一人恰一条未结束主任职由部分唯一索引 {@code uq_employment_primary} 约束）
     */
    private void seedEmployment(String personId, String orgUnitId, boolean primary, LocalDate endDate) {
        assertThat(endDate == null || !endDate.isBefore(EMPLOYMENT_START))
                .as("结束日不得早于起始日，否则违反 ck_employment_range")
                .isTrue();
        employments.insert(new Employment(Ids.newId(OrgIdPrefix.EMPLOYMENT), personId, orgUnitId,
                null, primary, EMPLOYMENT_START, endDate));
    }

    /** 供反向验证使用的档位集合，确认五档确实被逐一覆盖。 */
    private static Set<String> coveredScopes() {
        return Set.of(DeptScope.ALL.name(), DeptScope.CUSTOM.name(), DeptScope.DEPT.name(),
                DeptScope.DEPT_AND_SUB.name(), DeptScope.SELF.name());
    }
}
