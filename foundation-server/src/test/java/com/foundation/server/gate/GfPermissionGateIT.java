package com.foundation.server.gate;

import com.foundation.app.domain.App;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.ManifestApi;
import com.foundation.authz.api.ManifestImportResult;
import com.foundation.authz.api.OrgAdminScopeApi;
import com.foundation.authz.api.OrgAdminScopeCmd;
import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.RoleCmd;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.domain.SubjectMembership;
import com.foundation.common.api.error.BizException;
import com.foundation.common.api.error.ErrorCode;
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
import com.foundation.org.domain.Position;
import com.foundation.org.infrastructure.EmploymentRepository;
import com.foundation.org.infrastructure.OrgUnitRepository;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.org.infrastructure.PositionRepository;
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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 闸门 GF（F-00 §6.1 W83，BAS-05 / BAS-27）：权限中心五条验收口径在真实库上的端到端成立。
 *
 * <p>验收口径（F-00 §6.1 第 144 行、F-05 §19/§410）：① 权限清单导入幂等；② 岗位授权对新入职者生效；
 * ③ 互斥角色不能同时授予；④ 分级管理员无法越权；⑤ 三员分立。
 *
 * <p><b>与既有单测的分工</b>：{@code RoleMutexCheckerTest}、{@code SeparationHighRiskReviewTest}、
 * {@code OrgAdminScopeServiceTest}(5)、{@code OrgAdminScopeTest}(7)、{@code ManifestHasherTest}、
 * {@code ManifestDiffCalculatorTest}、{@code OrgAdminScopeGuardTest}(19) 全用内存 stub 验**算法**；
 * 本闸门跑完整 {@code FoundationApplication} + PostgreSQL 容器，验的是算法之下那层单测碰不到的东西：
 * <ul>
 *   <li>清单导入的幂等键 {@code (app_id, content_hash)} 与「版本只能前进」约束在真表上的唯一索引/事务边界；</li>
 *   <li>{@code SubjectMembership} 是否真装配了 {@link OrgSubjectMembership}——否则
 *       {@code postsOf}/{@code orgUnitsOf}/{@code descendantsOf} 恒空，②④ 会以「空 == 空」假绿；</li>
 *   <li>分级管理员拦截是否真的**接在授权路径上**（{@code RoleGrantService.grant} → {@code OrgAdminScopeGuard}）：
 *       算法单测再全，也测不出「guard 有没有被调用」，而这正是 BAS-27 曾经的生产缺口；</li>
 *   <li>三员互斥对种子（V4）与 {@code fdn_role_grant} 的真库落库/拒绝行为。</li>
 * </ul>
 *
 * <p><b>为什么关掉 {@code high-risk-review}</b>：三员角色全部带 HIGH 权限码
 * （{@code system_admin}: fdn:role:admin/grant:admin/sync:admin；{@code security_admin}: auth:policy:admin/…；
 * {@code audit_admin}: fdn:audit:export/fdn:audit:read:all）。而 {@code grant()} 的顺序是
 * 「{@code mutexGuard.check(existing)} → {@code deferIfHighRisk} → {@code insert}」，且 {@code existing}
 * 只读**已落库**授权：默认 {@code high-risk-review=true} 时，第一次授 {@code system_admin} 会转待复核而
 * **不落库**，第二次授 {@code audit_admin} 时 {@code existing} 仍为空 → **互斥检查根本不触发**，③⑤ 无从观测。
 * 关掉复核让授权真落库后互斥约束才可测（与既有单测 {@code SeparationHighRiskReviewTest} 同做法）。
 * 「关掉复核确实让高风险授权落库、且三员确实带 HIGH 权限码」这两个**前提**由
 * {@link #threeAdminRolesCarryHighRiskPermissionsSoReviewMustBeDisabled()} 钉住，避免前提悄悄变化时
 * ③⑤ 以难以理解的方式失败。
 *
 * <p><b>本类刻意不测「默认配置下两条高风险授权都进待复核队列」</b>：类级属性已把
 * {@code high-risk-review} 设为 false，在其中断言默认行为是名不副实的（测不到却声称测了）。
 * 该语义由既有单测 {@code SeparationHighRiskReviewTest#highRiskGrantTakesEffectOnlyAfterApproval} 覆盖
 * （它用默认 {@code SeparationProperties}，断言授权不落库 + 待复核队列 1 条），本闸门不重复覆盖、
 * 也不改生产代码。
 *
 * <p><b>清理口径</b>：gate job 内所有闸门共享同一个 PostgreSQL 容器（{@code FoundationContainers.PG} 是
 * static 单例，surefire 默认 forkCount=1 / reuseForks=true → 同一 JVM、Flyway 只跑一次、种子只种一次），
 * 故对**有迁移种子**的表（{@code fdn_app}/{@code fdn_role}/{@code fdn_role_mutex}/{@code fdn_role_permission}/
 * {@code fdn_permission_code}）只能按本闸门前缀 DELETE，绝不能 TRUNCATE——否则会清掉三员角色与互斥对种子，
 * 使同批次的其它闸门必败（此隐患曾由 PR #4 引入、PR #5 修复）。{@link #cleanUpPreservesSeedData()} 把这条
 * 约束做成可自动判定的信号。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest(properties = {
        // 见类 javadoc「为什么关掉 high-risk-review」：不关则互斥检查永不触发，③⑤ 无法观测
        "foundation.authz.separation.high-risk-review=false"
})
class GfPermissionGateIT {

    /** 组织树：闸门集团 → {华东区 → 华东销售部, 华北区}。 */
    private static final String ORG_ROOT = "GATE_PERM_ROOT";
    private static final String ORG_EAST = "GATE_PERM_EAST";
    private static final String ORG_EAST_SALES = "GATE_PERM_EAST_SALES";
    private static final String ORG_NORTH = "GATE_PERM_NORTH";

    /** 本闸门自建数据的统一前缀，清理时据此精确 DELETE（不碰迁移种子）。 */
    private static final String ROLE_PREFIX = "gate.role.";
    /**
     * 权限码前缀，**必须用冒号而非点号**：{@code PermissionCatalog.PERMISSION_CODE} 是
     * {@code ^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$}，即「资源:动作」形态且**不含点**。
     * 初版写成 {@code "gate.perm."} 会让 {@code ManifestParser} 在解析阶段就抛
     * {@code BAD_REQUEST 非法权限码}，验收① 一条断言都跑不到（CI run 19 实证）。
     * 该前缀同时用于清理的 {@code LIKE 'gateperm:%'}：字面部分不含 {@code _} 与 {@code %}，
     * 故不会被 LIKE 通配符误匹配到种子的 {@code fdn:*} 权限码。
     */
    private static final String PERM_PREFIX = "gateperm:";
    private static final String MENU_PREFIX = "gate.menu.";
    private static final String APP_CODE = "gate.perm.app";

    /** 未配 {@code fdn_org_admin_scope} 的操作人 → 走豁免分支，不受范围约束（见 OrgAdminScopeGuard）。 */
    private static final String GLOBAL_ADMIN = "gate-global-admin";
    /** 配了范围的操作人 → 是分级管理员，受范围与角色白名单约束。 */
    private static final String SCOPED_ADMIN = "gate-east-admin";

    /** 任职起始日：须早于任何 endDate（{@code fdn_employment} 有 {@code ck_employment_range}）。 */
    private static final LocalDate EMPLOYMENT_START = LocalDate.of(2026, 1, 1);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private ManifestApi manifests;

    @Autowired
    private GrantApi grants;

    @Autowired
    private RoleApi roles;

    @Autowired
    private OrgAdminScopeApi adminScopes;

    /** 被测端口的实际装配实现，用于挡住占位实现静默生效（②④ 成立的前提）。 */
    @Autowired
    private SubjectMembership membership;

    @Autowired
    private AppRepository apps;

    @Autowired
    private OrgUnitRepository orgUnits;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private EmploymentRepository employments;

    @Autowired
    private PositionRepository positions;

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
        //
        // **只 TRUNCATE 无迁移种子的表**（脚本全量扫描 33 个迁移的 INSERT INTO 得出：全仓有种子的是
        // fdn_app / fdn_permission_code / fdn_role / fdn_role_mutex / fdn_role_permission / fdn_dict_type /
        // fdn_dict_item / fdn_auth_login_mode / fdn_auth_policy 共 9 张）。fdn_outbox 是跨域共享表且
        // 无种子，但既有闸门（GfDataScopeGateIT / GfOrgImportGateIT）刻意不碰它以免跨闸门干扰，本闸门同样不碰。
        jdbc.execute("TRUNCATE fdn_person_dimension, fdn_role_dimension_value, fdn_role_data_scope, "
                + "fdn_role_grant, fdn_dimension_value, fdn_data_dimension, fdn_authz_snapshot, "
                + "fdn_high_risk_review, fdn_org_admin_scope, fdn_menu, fdn_app_manifest, "
                + "fdn_employment, fdn_report_line, fdn_external_identity, fdn_position, "
                + "fdn_org_change_log, fdn_job_grade, fdn_org_unit, fdn_person");
        // 有种子的 5 张表按前缀 DELETE。顺序：先删引用 fdn_role 的子表行，再删角色本身。
        // 注：fdn_role_mutex 的 DDL 只有 PRIMARY KEY、**没有** REFERENCES fdn_role(code)（已核实全仓无
        // ALTER 补 FK），故它与 fdn_role_permission 的先后不构成硬约束；仍按「子表先删」保守排序，
        // 以便将来若补上 FK 约束时无需改本清理逻辑。
        jdbc.update("DELETE FROM fdn_role_permission WHERE role_code LIKE ?", ROLE_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_role_mutex WHERE role_code LIKE ? OR mutex_role_code LIKE ?",
                ROLE_PREFIX + "%", ROLE_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_role WHERE code LIKE ?", ROLE_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_permission_code WHERE code LIKE ?", PERM_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_app WHERE code = ?", APP_CODE);
    }

    // ---------------------------------------------------------------- 前置与清理自证

    /**
     * 清理不得破坏迁移种子（V4 的三员角色 / 三员互斥对 / 内置权限码，V3+V4 的应用）。
     *
     * <p>{@code cleanUp()} 是 {@code @BeforeEach}，已在本用例前执行过，故此处直接断言种子仍在。
     * 这条断言把「gate job 内闸门共享同一 PG 容器、种子只种一次」这一隐含约束变成可自动判定的信号：
     * 若有人把清理改回 TRUNCATE 那几张有种子的表，本用例会红，而不是等同批次其它闸门静默失败后才发现。
     */
    @Test
    void cleanUpPreservesSeedData() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_role WHERE code IN ('system_admin','security_admin','audit_admin',"
                        + "'org_admin','app_admin')", Integer.class))
                .as("5 个内置角色种子必须在清理后仍在")
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_mutex", Integer.class))
                .as("三员互斥对种子（3 对）必须在清理后仍在")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_app WHERE code IN ('portal','admin','foundation')", Integer.class))
                .as("V3/V4 的三个内置应用种子必须在清理后仍在")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT role_code) FROM fdn_role_permission WHERE role_code IN "
                        + "('system_admin','security_admin','audit_admin','org_admin','app_admin')", Integer.class))
                .as("5 个内置角色的 fdn:* 权限码种子必须在清理后仍在")
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT manifest_version FROM fdn_app WHERE code = 'foundation'", Long.class))
                .as("本闸门用专用应用 gate.perm.app 导入清单，不得推进种子应用 foundation 的清单版本")
                .isEqualTo(0L);
    }

    /**
     * ②④ 成立的前提：{@code SubjectMembership} 必须装配真库实现。
     *
     * <p>{@code AuthzAutoConfiguration} 以 {@code @ConditionalOnMissingBean} 注册占位实现
     * {@code NoopSubjectMembership}（{@code postsOf}/{@code orgUnitsOf} 恒空、{@code orgUnitsOfPost} 走接口
     * default 恒空）。若 {@code FoundationServerAssemblyConfiguration} 因故未生效，②的岗位授权会「查不到人」、
     * ④的范围展开会「查不到子树」，弱断言可能以「空 == 空」假绿。故把装配实现本身做成独立信号，
     * 并顺带验证三条真库读取路径都通（岗位→组织单元这条是 PR #6 新加的，尤其要验）。
     */
    @Test
    void assemblyUsesRealMembershipNotPlaceholder() {
        assertThat(membership)
                .as("SubjectMembership 必须装配真库实现而非占位实现")
                .isInstanceOf(OrgSubjectMembership.class);

        seedOrgTree();
        String positionId = seedPosition(ORG_EAST_SALES);
        String personId = seedPerson("P0001");
        seedEmployment(personId, eastSalesId, positionId, true, null);

        assertThat(membership.orgUnitsOf(personId))
                .as("真库实现应能从 fdn_employment 读出组织归属")
                .containsExactly(eastSalesId);
        assertThat(membership.postsOf(personId))
                .as("真库实现应能从 fdn_employment 读出岗位归属")
                .containsExactly(positionId);
        assertThat(membership.orgUnitsOfPost(positionId))
                .as("真库实现应能从 fdn_position 读出岗位所属组织单元（分级管理员拦截 POST 型授权的前提）")
                .containsExactly(eastSalesId);
        assertThat(membership.descendantsOf(eastId))
                .as("子树应含自身与下级（物化路径前缀匹配）")
                .containsExactlyInAnyOrder(eastId, eastSalesId)
                .doesNotContain(rootId, northId);
    }

    // ---------------------------------------------------------------- ① 清单导入幂等

    /**
     * ① 权限清单导入幂等：同内容重复导入命中 {@code (app_id, content_hash)} 直接返回
     * {@code idempotent=true} 且不重复落库；内容变了但版本没前进则 {@code CONFLICT}；版本前进则正常导入。
     */
    @Test
    void manifestImportIsIdempotentAndVersionMustAdvance() {
        seedApp();

        ManifestImportResult first = manifests.importManifest(APP_CODE, manifest(1, "示例读取"),
                "ci@contract", GLOBAL_ADMIN);
        assertThat(first.idempotent()).as("首次导入不应命中幂等").isFalse();
        assertThat(first.version()).isEqualTo(1);
        assertThat(first.menuCount()).isEqualTo(1);
        assertThat(first.permissionCodeCount()).isEqualTo(1);
        assertThat(first.contentHash()).isNotBlank();

        // 同内容重复导入：命中 (app_id, content_hash) → idempotent=true，且不产生第二条清单记录
        ManifestImportResult again = manifests.importManifest(APP_CODE, manifest(1, "示例读取"),
                "ci@contract", GLOBAL_ADMIN);
        assertThat(again.idempotent()).as("同内容重复导入必须幂等返回").isTrue();
        assertThat(again.contentHash()).isEqualTo(first.contentHash());
        assertThat(again.version()).as("幂等命中应返回既有记录的版本").isEqualTo(first.version());
        // 刻意**不**断言 importedAt 完全相等：首次返回的是内存 Instant.now()（纳秒精度），
        // 幂等返回的是从 TIMESTAMPTZ 列读回的值（PostgreSQL 只存到微秒），直接 isEqualTo 会因精度
        // 截断而必然失败。这里只要求它落在首次导入那一刻或之后——足以证明「返回的是既有记录的时间戳
        // 而非重新生成的」，同时不把与验收无关的时间精度做成断言前提。
        assertThat(again.importedAt())
                .as("幂等命中应返回既有记录的导入时间（同一时刻，受列精度限制只比较到秒）")
                .isBetween(first.importedAt().minusSeconds(1), first.importedAt().plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_app_manifest WHERE app_id = "
                        + "(SELECT id FROM fdn_app WHERE code = ?)", Integer.class, APP_CODE))
                .as("幂等命中不得重复落库清单记录")
                .isEqualTo(1);
        assertThat(countPermissionCodes()).as("幂等命中不得重复插入权限码").isEqualTo(1);

        // 内容变了但版本没前进 → CONFLICT（「清单版本只能前进」）
        assertThatThrownBy(() -> manifests.importManifest(APP_CODE, manifest(1, "改名但未升版本"),
                "ci@contract", GLOBAL_ADMIN))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        // 版本前进 → 正常导入，权限码按差异更新而非重复插入
        ManifestImportResult v2 = manifests.importManifest(APP_CODE, manifest(2, "示例读取"),
                "ci@contract", GLOBAL_ADMIN);
        assertThat(v2.idempotent()).isFalse();
        assertThat(v2.version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT manifest_version FROM fdn_app WHERE code = ?", Long.class, APP_CODE))
                .as("导入成功后 fdn_app.manifest_version 应被推进")
                .isEqualTo(2L);
        assertThat(countPermissionCodes()).as("同 code 的权限码应被更新而非重复插入").isEqualTo(1);

        // 清单不存在的 appCode → BAD_REQUEST/NOT_FOUND（AppIdResolver.resolve 对未知 code 抛 NOT_FOUND）
        assertThatThrownBy(() -> manifests.importManifest("gate.perm.unknown", manifest(1, "x"),
                "ci@contract", GLOBAL_ADMIN))
                .isInstanceOf(BizException.class);
    }

    // ---------------------------------------------------------------- ② 岗位授权对新入职者生效

    /**
     * ② 岗位授权对新入职者生效：给岗位授角色后，**之后才入职**并挂该岗位的人无需任何补授权，
     * 经 {@code effectiveRoles} 就能拿到该角色（{@code EffectiveRoleResolver.collectByPost} 用
     * {@code membership.postsOf(personId)} 与 {@code grants.list(POST, null)} 匹配）。
     *
     * <p>顺序是关键：先授岗位、后造人入职。若反过来，就测不出「对新入职者生效」而只是「对已有人员生效」。
     */
    @Test
    void postGrantTakesEffectForLaterHires() {
        seedOrgTree();
        String grantedPost = seedPosition(ORG_EAST_SALES);
        String siblingPost = seedPosition(ORG_EAST_SALES);
        String roleCode = ROLE_PREFIX + "post";
        roles.create(new RoleCmd(roleCode, roleCode, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        grants.grant(new RoleGrantCmd(roleCode, "POST", grantedPost, false,
                null, null, "MANUAL", GLOBAL_ADMIN));

        // 授权之后才入职的新人（主任职挂被授权的岗位）
        String hire = seedPerson("P0100");
        seedEmployment(hire, eastSalesId, grantedPost, true, null);
        assertThat(grants.effectiveRoles(hire, null, Instant.now()))
                .as("授权后新入职并挂该岗位的人应自动获得岗位角色（无需补授权）")
                .contains(roleCode);

        // 关键对照：**同一组织单元**、挂**另一个未被授权岗位**的人。
        // 用同组织单元是刻意的——若拿「另一个组织单元的人」做对照，他拿不到角色只是因为没被授权，
        // 换成任何未授权者都成立，证明不了生效路径是「岗位」而非「组织单元」（collectByOrgUnit）。
        String sibling = seedPerson("P0101");
        seedEmployment(sibling, eastSalesId, siblingPost, true, null);
        assertThat(grants.effectiveRoles(sibling, null, Instant.now()))
                .as("同组织单元但挂未授权岗位的人不应获得该角色（证明生效路径是岗位而非组织单元）")
                .doesNotContain(roleCode);

        // 已结束的任职不再生效（openByPerson 过滤 end_date IS NULL）
        String leaver = seedPerson("P0102");
        seedEmployment(leaver, eastSalesId, grantedPost, true, EMPLOYMENT_START.plusDays(1));
        assertThat(grants.effectiveRoles(leaver, null, Instant.now()))
                .as("任职已结束的人即使挂的是被授权岗位也不应获得角色")
                .doesNotContain(roleCode);
    }

    // ---------------------------------------------------------------- ③ 互斥角色不能同时授予

    /**
     * ③ 互斥角色不能同时授予：命中 {@code fdn_role_mutex} 的互斥对时第二次授予抛 {@code CONFLICT}、
     * 不落库，且有效角色只保留一个。
     *
     * <p>用**自建互斥对**而非种子三员，以便与 ⑤（专测三员分立）分工：本用例验的是互斥机制本身
     * 在真库上的落库/拒绝行为与 {@code RoleMutexRepository.all()} 能否读到 JDBC 直插的互斥对。
     */
    @Test
    void mutexRolesCannotBeGrantedTogether() {
        seedOrgTree();
        String left = ROLE_PREFIX + "mutex.left";
        String right = ROLE_PREFIX + "mutex.right";
        roles.create(new RoleCmd(left, left, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        roles.create(new RoleCmd(right, right, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        // RoleMutexRepository 只有只读 all()，互斥对在生产里来自 V4 种子，故此处按种子的同款形态 JDBC 直插
        jdbc.update("INSERT INTO fdn_role_mutex (role_code, mutex_role_code) VALUES (?, ?)", left, right);

        String personId = seedPerson("P0200");
        seedEmployment(personId, eastId, null, true, null);

        grants.grant(new RoleGrantCmd(left, "PERSON", personId, false, null, null, "MANUAL", GLOBAL_ADMIN));
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(right, "PERSON", personId, false,
                null, null, "MANUAL", GLOBAL_ADMIN)))
                .as("授予与已有角色互斥的角色必须被拒")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_grant WHERE subject_id = ?",
                Integer.class, personId))
                .as("被拒的互斥授权不得落库")
                .isEqualTo(1);
        assertThat(grants.effectiveRoles(personId, null, Instant.now()))
                .containsExactly(left);
    }

    // ---------------------------------------------------------------- ⑤ 三员分立

    /**
     * ⑤ 三员分立：系统管理员 / 安全管理员 / 审计管理员三者两两互斥，任一人不能同时持有其中两个。
     *
     * <p>互斥对来自 V4 种子（本闸门不造，只消费），故本用例同时验证「种子互斥对能被真库的
     * {@code RoleMutexGuard} 读到并生效」。三对组合逐一验证，避免只测一对而漏掉种子缺项。
     */
    @Test
    void threeAdminRolesAreMutuallyExclusive() {
        // 先断言种子互斥对完整（3 对，单向存储），否则下面的行为断言可能因种子缺项而「碰巧通过」。
        // 注意 fdn_role_mutex 只存**单向**对（RoleMutexChecker.normalize 在读取时才归一为对称），
        // 故这里是 3 行而不是 6 行。
        assertThat(jdbc.queryForList("SELECT role_code || '|' || mutex_role_code AS pair FROM fdn_role_mutex "
                        + "ORDER BY pair", String.class))
                .as("V4 种子的三员互斥对必须两两齐全")
                .containsExactlyInAnyOrder("security_admin|audit_admin", "system_admin|audit_admin",
                        "system_admin|security_admin");

        seedOrgTree();
        assertMutexPair("system_admin", "security_admin", "P0300");
        assertMutexPair("system_admin", "audit_admin", "P0301");
        assertMutexPair("security_admin", "audit_admin", "P0302");
    }

    /** 逐对验证：先授 left 成功，再授 right 被拒且有效角色只剩 left。每对用独立人员，避免互相干扰。 */
    private void assertMutexPair(String left, String right, String empNo) {
        String personId = seedPerson(empNo);
        seedEmployment(personId, eastId, null, true, null);

        grants.grant(new RoleGrantCmd(left, "PERSON", personId, false, null, null, "MANUAL", GLOBAL_ADMIN));
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(right, "PERSON", personId, false,
                null, null, "MANUAL", GLOBAL_ADMIN)))
                .as("三员分立：%s 已授予时不得再授 %s", left, right)
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        assertThat(grants.effectiveRoles(personId, null, Instant.now()))
                .as("三员分立：%s 与 %s 不得同时生效", left, right)
                .containsExactly(left);
    }

    /**
     * ⑤ 的前提自证：三员角色确实带 HIGH 权限码，且本类关掉复核后高风险授权真的落库。
     *
     * <p>这条用例守的是「为什么必须关掉 {@code high-risk-review} 才能观测互斥」这个前提本身。
     * 若将来有人给三员角色去掉 HIGH 权限码，或把 {@code deferIfHighRisk} 的短路条件改掉，
     * ③⑤ 的观测基础就会悄悄变化，本用例会先红——而不是等到互斥用例以难以理解的方式失败。
     *
     * <p><b>刻意不在此测「默认配置下两条高风险授权都进待复核队列」</b>：本类的类级属性已把
     * {@code high-risk-review} 设为 false，在这里断言默认行为是**名不副实**的（测不到却声称测了）。
     * 该语义由既有单测 {@code SeparationHighRiskReviewTest#highRiskGrantTakesEffectOnlyAfterApproval}
     * 覆盖（它用默认 {@code SeparationProperties}，断言授权不落库 + 待复核队列 1 条），
     * 本闸门不重复覆盖，只把前提钉住。
     */
    @Test
    void threeAdminRolesCarryHighRiskPermissionsSoReviewMustBeDisabled() {
        seedOrgTree();
        String personId = seedPerson("P0400");
        seedEmployment(personId, eastId, null, true, null);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_permission WHERE role_code = 'system_admin' "
                        + "AND code IN (SELECT code FROM fdn_permission_code WHERE risk_level = 'HIGH')", Integer.class))
                .as("system_admin 必须带 HIGH 权限码，否则默认配置下会直接落库而非转复核")
                .isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_permission WHERE role_code = 'audit_admin' "
                        + "AND code IN (SELECT code FROM fdn_permission_code WHERE risk_level = 'HIGH')", Integer.class))
                .as("audit_admin 必须带 HIGH 权限码（同上）")
                .isPositive();

        grants.grant(new RoleGrantCmd("system_admin", "PERSON", personId, false,
                null, null, "MANUAL", GLOBAL_ADMIN));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_grant WHERE subject_id = ?",
                Integer.class, personId))
                .as("关掉 high-risk-review 后高风险授权应真落库（这正是互斥可被观测的前提）")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_high_risk_review", Integer.class))
                .as("关掉 high-risk-review 后不应产生待复核记录")
                .isEqualTo(0);
    }

    // ---------------------------------------------------------------- ④ 分级管理员无法越权

    /**
     * ④ 分级管理员无法越权授予：已配 {@code fdn_org_admin_scope} 的操作人只能把**白名单内**的角色
     * 授予**范围内**（组织子树）的对象；越权抛 {@code FORBIDDEN} 且不落库。
     *
     * <p>本用例走**生产路径** {@code GrantApi.grant}（内部经 {@code OrgAdminScopeGuard}），而非直接调
     * {@code OrgAdminScopeService.assertGrantable}——后者只有算法单测覆盖，测不出「guard 是否真接在授权路径上」，
     * 而那正是 BAS-27 曾经的生产缺口（能力齐备但零调用方）。
     */
    @Test
    void scopedAdminCannotGrantBeyondScope() {
        seedOrgTree();
        String allowed = ROLE_PREFIX + "hr";
        String notListed = ROLE_PREFIX + "finance";
        roles.create(new RoleCmd(allowed, allowed, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        roles.create(new RoleCmd(notListed, notListed, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));

        // 华东区分级管理员：范围 = 华东区含下级，角色白名单 = 只有 gate.role.hr
        adminScopes.upsert(new OrgAdminScopeCmd(SCOPED_ADMIN, eastId, true, List.of(allowed)), GLOBAL_ADMIN);

        // 范围内 + 白名单内 → 放行（先验合法路径，否则「全拒」也能让越权断言通过）
        assertThatCode(() -> grants.grant(new RoleGrantCmd(allowed, "ORG_UNIT", eastSalesId, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("范围内的组织单元 + 白名单内的角色应放行")
                .doesNotThrowAnyException();

        // 范围外（华北区不在华东区子树内）→ FORBIDDEN
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(allowed, "ORG_UNIT", northId, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("分级管理员不得授予范围外的组织单元")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 范围内但白名单外 → FORBIDDEN
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(notListed, "ORG_UNIT", eastSalesId, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("分级管理员不得授予白名单外的角色")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(jdbc.queryForList("SELECT subject_id FROM fdn_role_grant WHERE role_code = ?",
                        String.class, notListed))
                .as("越权授予不得落库")
                .isEmpty();
        assertThat(jdbc.queryForList("SELECT subject_id FROM fdn_role_grant WHERE role_code = ? AND subject_id = ?",
                        String.class, allowed, northId))
                .as("范围外的越权授予不得落库")
                .isEmpty();
    }

    /**
     * ④ 的 PERSON 型口径：目标是**全部未结束任职**的组织单元（主任职 + 兼职），全量 AND。
     *
     * <p>只校验主任职会留下越权通道：{@code EffectiveRoleResolver.collectByOrgUnit} 会让被授权人在其
     * **所有**挂靠组织获得角色，故华东区管理员可借「主任职华东、兼职华北」的人间接影响华北区。
     */
    @Test
    void scopedAdminIsRejectedWhenPersonHasOutOfScopeConcurrentPost() {
        seedOrgTree();
        String allowed = ROLE_PREFIX + "hr";
        roles.create(new RoleCmd(allowed, allowed, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        adminScopes.upsert(new OrgAdminScopeCmd(SCOPED_ADMIN, eastId, true, List.of(allowed)), GLOBAL_ADMIN);

        // 主任职在华东销售部（范围内）
        String inScope = seedPerson("P0500");
        seedEmployment(inScope, eastSalesId, null, true, null);
        assertThatCode(() -> grants.grant(new RoleGrantCmd(allowed, "PERSON", inScope, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("全部任职都在范围内时应放行")
                .doesNotThrowAnyException();

        // 主任职在华东销售部（范围内）+ 兼职在华北区（范围外）→ 拒绝
        String crossRegion = seedPerson("P0501");
        seedEmployment(crossRegion, eastSalesId, null, true, null);
        seedEmployment(crossRegion, northId, null, false, null);
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(allowed, "PERSON", crossRegion, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("兼职挂靠超出范围时不得放行（否则可借兼职影响外区）")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_grant WHERE subject_id = ?",
                Integer.class, crossRegion))
                .as("被拒的越权授权不得落库")
                .isEqualTo(0);
    }

    /**
     * ④ 的豁免边界：**未配任何范围**的操作人不受约束（全局管理员照常授权）。
     *
     * <p>这不只是宽松取舍——{@code assertGrantable} 在 {@code scopes.list()} 为空时会走到循环外抛
     * {@code FORBIDDEN}，若无条件接入会打挂所有现有授权流程。故豁免分支必须真库验证，
     * 否则「接线把全局管理员也锁死」这种严重回归会静默通过（本类其它用例都用 {@code GLOBAL_ADMIN}
     * 授权，已隐式覆盖，但显式断言一次使意图可回归）。
     */
    @Test
    void adminWithoutScopeIsNotRestricted() {
        seedOrgTree();
        String roleCode = ROLE_PREFIX + "free";
        roles.create(new RoleCmd(roleCode, roleCode, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_org_admin_scope WHERE user_id = ?",
                Integer.class, GLOBAL_ADMIN))
                .as("前置：该操作人未配任何分级管理范围")
                .isEqualTo(0);

        assertThatCode(() -> grants.grant(new RoleGrantCmd(roleCode, "ORG_UNIT", northId, false,
                null, null, "MANUAL", GLOBAL_ADMIN)))
                .as("未配范围者不是分级管理员，授权不应受范围约束")
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_grant WHERE subject_id = ?",
                Integer.class, northId))
                .isEqualTo(1);
    }

    /**
     * ④ 的 POST 型：目标组织单元经 {@code membership.orgUnitsOfPost} 从真库解析（PR #6 新增的端口方法）。
     *
     * <p>顺带钉住 fail-closed 语义：岗位未归属任何组织单元时**拒绝**而非放行——{@code collectByPost}
     * 会让该岗位下所有人都获得角色，放行即等于允许借「未归属岗位」越权。
     */
    @Test
    void scopedAdminGrantToPostIsResolvedThroughRealMembership() {
        seedOrgTree();
        String allowed = ROLE_PREFIX + "hr";
        roles.create(new RoleCmd(allowed, allowed, "SYSTEM", null, null, List.of(), GLOBAL_ADMIN));
        adminScopes.upsert(new OrgAdminScopeCmd(SCOPED_ADMIN, eastId, true, List.of(allowed)), GLOBAL_ADMIN);

        // 岗位归属华东销售部（范围内）→ 放行
        String inScopePost = seedPosition(ORG_EAST_SALES);
        assertThatCode(() -> grants.grant(new RoleGrantCmd(allowed, "POST", inScopePost, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("岗位归属在范围内时应放行")
                .doesNotThrowAnyException();

        // 岗位归属华北区（范围外）→ 拒绝
        String outScopePost = seedPosition(ORG_NORTH);
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(allowed, "POST", outScopePost, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("岗位归属超出范围时不得放行")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        // 岗位未归属组织单元（org_unit_id 可空 = 全局岗位）→ fail-closed 拒绝
        String globalPost = insertPosition("GATE_PERM_GLOBAL_POST", null);
        assertThatThrownBy(() -> grants.grant(new RoleGrantCmd(allowed, "POST", globalPost, false,
                null, null, "MANUAL", SCOPED_ADMIN)))
                .as("解析不出目标组织单元时必须拒绝（fail-closed），否则可借全局岗位越权")
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---------------------------------------------------------------- 造数

    private int countPermissionCodes() {
        return jdbc.queryForObject("SELECT count(*) FROM fdn_permission_code WHERE code LIKE ?",
                Integer.class, PERM_PREFIX + "%");
    }

    /**
     * 造本闸门专用的应用（而非复用种子的 {@code foundation}）。
     *
     * <p>原因：清单导入会经 {@code AppManifestVersionService.advance} **改写 fdn_app.manifest_version**，
     * 而该列在种子行上。用种子应用会让「清理后种子完好」不可逆地被本闸门破坏（且 gate job 内闸门共享容器，
     * 会污染同批次其它闸门）。用专用应用 + 按 code 精确 DELETE，既不碰种子又可重复运行。
     */
    private void seedApp() {
        apps.insert(App.register("app_gate_perm", APP_CODE, "闸门权限清单应用", null, null, "ADMIN", "system"),
                GLOBAL_ADMIN);
    }

    /**
     * 生成清单原文。{@code version} 与 {@code permissionName} 共同决定内容哈希：
     * 同参数 → 同哈希（验幂等命中）；改 {@code permissionName} 而版本不变 → 不同哈希但版本未前进（验 CONFLICT）。
     */
    private static byte[] manifest(long version, String permissionName) {
        String json = """
                {
                  "version": %d,
                  "menus": [
                    {
                      "code": "%sexample",
                      "name": "闸门示例菜单",
                      "type": "MENU",
                      "routePath": "/gate-example",
                      "component": "gate/example",
                      "sort": 1,
                      "visible": true,
                      "permissionCode": "%sexample"
                    }
                  ],
                  "permissions": [
                    {
                      "code": "%sexample",
                      "name": "%s",
                      "riskLevel": "LOW"
                    }
                  ]
                }
                """.formatted(version, MENU_PREFIX, PERM_PREFIX, PERM_PREFIX, permissionName);
        return json.getBytes(StandardCharsets.UTF_8);
    }

    /** 闸门集团 → {华东区 → 华东销售部, 华北区}，物化路径由 {@link OrgTreePolicy} 生成。 */
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

    /**
     * 造岗位并返回其 ID。
     *
     * <p>{@code orgUnitCode} 必须显式映射到已造的组织单元：**不设 default 分支**——若拼错组织编码而落到
     * 「全局岗位（org_unit_id 为 null）」，用例会在毫无线索的情况下测到另一条路径（fail-closed 拒绝）而假绿。
     * 岗位 code 带上生成的 ID 后缀，故同一用例内可多次为同一组织单元造岗位而不撞 {@code UNIQUE (code)}。
     */
    private String seedPosition(String orgUnitCode) {
        String orgUnitId;
        if (ORG_ROOT.equals(orgUnitCode)) {
            orgUnitId = rootId;
        } else if (ORG_EAST.equals(orgUnitCode)) {
            orgUnitId = eastId;
        } else if (ORG_EAST_SALES.equals(orgUnitCode)) {
            orgUnitId = eastSalesId;
        } else if (ORG_NORTH.equals(orgUnitCode)) {
            orgUnitId = northId;
        } else {
            throw new IllegalArgumentException("未先造组织单元就引用其编码：" + orgUnitCode);
        }
        return insertPosition(orgUnitCode + "_POST", orgUnitId);
    }

    private String insertPosition(String codePrefix, String orgUnitId) {
        String id = Ids.newId(OrgIdPrefix.POSITION);
        positions.insert(new Position(id, codePrefix + "_" + id, "闸门岗位-" + codePrefix, orgUnitId,
                OrgStatus.ACTIVE, 0, "local", null));
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
     * @param endDate 非 null 表示已结束任职；{@code primary=false} 表示兼职
     *                （一人恰一条未结束主任职由部分唯一索引 {@code uq_employment_primary} 约束）
     */
    private void seedEmployment(String personId, String orgUnitId, String positionId,
                                boolean primary, LocalDate endDate) {
        assertThat(endDate == null || !endDate.isBefore(EMPLOYMENT_START))
                .as("结束日不得早于起始日，否则违反 ck_employment_range")
                .isTrue();
        employments.insert(new Employment(Ids.newId(OrgIdPrefix.EMPLOYMENT), personId, orgUnitId,
                positionId, primary, EMPLOYMENT_START, endDate));
    }

    /**
     * 五条验收口径的覆盖自证：F-00 §6.1 W83 点名的五条验收口径必须**各有对应的 @Test 方法**。
     *
     * <p>为什么值得单独一条用例：本类另有若干辅助/边界用例（装配自证、豁免边界、POST 型 fail-closed、
     * 清理不破坏种子等），若无这条清单，「闸门已建」很容易与「验收点已覆盖」被混为一谈——
     * 删掉或改名任一条验收用例，闸门仍会以其余用例全绿而通过，缺哪条验收点无从发现。
     *
     * <p>方法名在此**刻意硬编码**：这是有意的脆弱性——改名任一验收用例必须同步更新本清单，
     * 从而强迫改动者显式确认「这条验收口径仍被覆盖」。
     */
    @Test
    void allFiveAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "manifestImportIsIdempotentAndVersionMustAdvance",           // ① 权限清单导入幂等
                "postGrantTakesEffectForLaterHires",                         // ② 岗位授权对新入职者生效
                "mutexRolesCannotBeGrantedTogether",                         // ③ 互斥角色不能同时授予
                "scopedAdminCannotGrantBeyondScope",                         // ④ 分级管理员无法越权
                "threeAdminRolesAreMutuallyExclusive");                      // ⑤ 三员分立

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-00 §6.1 W83 的五条验收口径必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }
}
