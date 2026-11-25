package com.foundation.server.gate;

import com.foundation.account.api.AccountApi;
import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.application.AccountPushService;
import com.foundation.account.application.AccountReconcileJob;
import com.foundation.account.domain.AccountReconcileRecord;
import com.foundation.common.api.keycloak.KeycloakUserAdmin;
import com.foundation.common.api.keycloak.KeycloakUserView;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1 W82，F-04 §6 / BAS-03）：账号生命周期与每日对账在**真实 Keycloak** 上成立。
 *
 * <p>验收口径（F-00 §6.1）：停用后 Keycloak 登录失败；人为改 Keycloak 后每日对账发现并修复。
 *
 * <p><b>本闸门验什么，而既有单测验不到</b>：{@code ReconcileDiffTest} 的 8 个用例已用内存快照覆盖
 * **算法**（enabled/username/requiredActions 漂移检测、{@code MISSING_IN_KEYCLOAK} 不可就地修、
 * orphan 忽略 {@code service-account-} 前缀、按百分比与按数量两种阈值）。本闸门跑完整
 * {@code FoundationApplication} + PostgreSQL + **真实 Keycloak 容器**，验的是算法之下那层
 * stub 碰不到的东西：
 * <ul>
 *   <li>{@code AccountPushService.push} 是**异步**的（只写 {@code fdn_account_push} 待发记录），
 *       真正调 Keycloak 在 {@code flushDue}。本闸门据此断言「停用后 Keycloak 侧真的变
 *       {@code enabled=false}」，即**异步推送链路端到端接通**——这是 F-04 §5「Foundation 不存口令、
 *       生命周期动作以 Keycloak 为准」的实际兑现，内存 stub 完全测不到；</li>
 *   <li>「人为改 Keycloak」用的是**真实 Admin API 写真实 Keycloak**（{@code keycloak.setEnabled(...)}），
 *       再由 {@code AccountReconcileJob.reconcileOnce()} 从**真实 Keycloak 分页拉全量**
 *       （{@code listUsers()}）比对并回正。整条「读真 Keycloak → 比对 → 写真 Keycloak」回路
 *       是本闸门的核心价值；</li>
 *   <li>{@code keycloak_user_id} 回写：推送成功后 {@code fdn_account.keycloak_user_id} 必须被填上，
 *       这是后续所有生命周期动作（停用 / 改密 / 吊销会话）能找到 Keycloak 用户的前提。</li>
 * </ul>
 *
 * <p><b>关于「停用后 Keycloak 登录失败」的如实标注</b>：本闸门断言的是 **Keycloak 侧
 * {@code enabled=false} 且该用户会话已被吊销**——这是「登录失败」在平台侧的可验证等价物。
 * **不声称做了真实口令登录失败验证**，原因：测试 realm（{@code deploy/init/foundation-realm.json}）
 * 的 {@code foundation-admin} client 是 {@code directAccessGrantsEnabled: false}，
 * 未开启 password grant，故无法用 ROPC 直连验证登录被拒。为此改生产 realm 配置属**安全配置变更**
 * （开启 password grant 本身是不良实践），不在本闸门范围内擅自做。若将来要补端到端登录验证，
 * 应新增专用测试 client 并单独评估。
 *
 * <p><b>为什么显式把 {@code drift-threshold-percent} 设为 0</b>：{@code ReconcileDiff.exceedsThreshold}
 * 是 {@code drifted * 100 > total * thresholdPercent}。默认阈值 5%，而本闸门只造**个位数账号**——
 * 例如 1 个账号漂移 1 条即 {@code 100 > 5} → 判定为**超阈值**，于是 {@code held=true} 且
 * **暂停修复**（{@code fixed=0}）。若不显式关掉百分比阈值，「对账修复」用例会以
 * 「Keycloak 没被回正」的形态失败，而根因（走了 held 分支）完全看不出来。
 * 故本类把 percent 置 0（{@code thresholdPercent > 0} 才参与判定）并保留 count=50 默认。
 * **held 分支本身不在此重复测**——它是纯算法，已由 {@code ReconcileDiffTest.thresholdByPercent}
 * 与 {@code thresholdByCount} 覆盖。
 *
 * <p><b>关于「每日」</b>：{@code runDaily()} 带 {@code @Scheduled(cron = "0 30 2 * * *")}，
 * 闸门不可能等真实 cron，故直接调 {@code reconcileOnce()}（{@code runDaily()} 的方法体就是它）。
 * 这样验的是**对账逻辑本身**，不验调度器触发——后者属 {@code @Scheduled} 装配的范畴，
 * 由 {@code AccountAutoConfiguration} 开启 {@code @EnableScheduling} 保证，不在本闸门范围。
 *
 * <p><b>共享容器口径（关键）</b>：gate job 内所有闸门共享同一个 PostgreSQL 与 Keycloak 容器
 * （{@code FoundationContainers.PG}/{@code keycloak()} 都是 static 单例）。两点后果与本闸门的处置：
 * <ul>
 *   <li>{@code reconcileOnce()} 内部调 {@code accounts.findAll()} 是**全表对账**。已核实 gate job 内
 *       **只有本闸门**往 {@code fdn_account} 写数据（{@code GfOrgImportGateIT} 只清
 *       {@code fdn_external_identity}、不建账号；{@code GfSnapshotEventGateIT} 只碰 authz/org 表），
 *       故全表对账不会误改别的闸门造的账号。<b>若将来有其它闸门开始建账号，本闸门的
 *       {@code total}/{@code drifted} 断言会受影响，届时必须改为按本闸门账号子集断言。</b></li>
 *   <li>Keycloak 是共享 realm。已核实 {@code ReconcileDiff} 对 {@code service-account-} 前缀的用户
 *       显式 {@code continue}（不会判为 orphan 而停用），故 realm 种子用户
 *       {@code service-account-foundation-admin} 安全；{@code KeycloakAdminClientIT} 无
 *       {@code @Tag("gate")}，在独立 job 跑，不共享本容器。本闸门自建的 Keycloak 用户在
 *       {@link #cleanUpKeycloak()} 里显式删除，不留残留给同批次其它闸门。</li>
 * </ul>
 *
 * <p><b>清理口径</b>：{@code fdn_account} / {@code fdn_account_push} / {@code fdn_account_reconcile}
 * 均无迁移种子（已核实全仓 33 个迁移的 INSERT 不含这三张表），但 gate job 内共享，
 * 故按本闸门前缀 DELETE 而非 TRUNCATE，避免误删同批次其它闸门将来可能造的数据。
 * {@code fdn_role}/{@code fdn_app} 等有 V3/V4 种子的表本闸门完全不碰。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest(properties = {
        // 见类 javadoc「为什么显式把 drift-threshold-percent 设为 0」
        "foundation.account.reconcile.drift-threshold-percent=0",
        // 把定时对账推到不会在测试期间自行触发的时刻，避免调度器与测试线程争抢同一批账号
        // （与 IntegrationTestSupport 关掉后台调度器同一理由：单个测试管不住其它缓存上下文的调度器）
        "foundation.account.reconcile.cron=0 0 4 1 1 ? 2099"
})
class GfAccountLifecycleGateIT {

    /** 本闸门自建数据的统一前缀，清理时据此精确 DELETE。 */
    private static final String LOGIN_PREFIX = "gfacct";
    private static final String EMP_PREFIX = "L0";

    private static final String OPERATOR = "gate";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
        FoundationContainers.registerKeycloak(registry);
    }

    @Autowired
    private AccountApi accounts;

    @Autowired
    private AccountReconcileJob reconcileJob;

    /** 推送是异步的，闸门需显式 flush（不经 reconcileOnce，理由见 {@link #flushPushes()}）。 */
    @Autowired
    private AccountPushService pushService;

    /** 直接操作真实 Keycloak，用于制造「人为改动」与读回验证。 */
    @Autowired
    private KeycloakUserAdmin keycloak;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        cleanUpKeycloak();
        // 逆外键依赖顺序：push 记录引用 account，故先删 push 再删 account
        jdbc.update("DELETE FROM fdn_account_push WHERE account_id IN "
                + "(SELECT id FROM fdn_account WHERE login_name LIKE ?)", LOGIN_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_account WHERE login_name LIKE ?", LOGIN_PREFIX + "%");
        // 对账记录是只追加表，无法按账号过滤，故按本闸门执行时段清理见 cleanUpReconcileRecords()
        jdbc.update("DELETE FROM fdn_account_reconcile");
        jdbc.update("DELETE FROM fdn_person WHERE emp_no LIKE ?", EMP_PREFIX + "%");
    }

    @AfterEach
    void cleanUpKeycloak() {
        // 共享 Keycloak 容器：本闸门建的用户必须删掉，否则会被同批次其它闸门的 listUsers 看到。
        //
        // **按登录名前缀扫描删除，而非只删自己记下的 createdLogins**：对账用例会人为把用户改名
        // （再被对账改回），任一步抛异常都可能让 Keycloak 里留下本闸门不认识的残留用户。
        // 前缀扫描是自愈的——不管中途发生什么，只要用户名带 gfacct 前缀就一定被清掉。
        // 已核实 realm 种子用户是 service-account-foundation-admin，不含该前缀，不会被误删。
        for (KeycloakUserView user : keycloak.listUsers()) {
            String username = user.username();
            if (username != null && username.startsWith(LOGIN_PREFIX)) {
                keycloak.deleteUser(user.id());
            }
        }
    }

    // ---------------------------------------------------------------- ① 开通与推送

    /**
     * ① 开通个人账号后，推送链路真的把用户建到 Keycloak，并回写 {@code keycloak_user_id}。
     *
     * <p>这是后续两条验收口径的**共同前提**：若推送链路没接通，「停用后 Keycloak 登录失败」与
     * 「对账修复」都无从谈起。把它单列一条用例，是为了在前提失败时给出明确信号，
     * 而不是让后面两条以「Keycloak 里找不到用户」这种间接形态失败。
     *
     * <p>注意 {@code push} 是异步的（只写待发记录），故必须显式 {@code flushDue} 才会真调 Keycloak。
     * 「不 flush 就查不到用户」本身也是本用例要钉住的语义——它证明推送确实走待发队列而非同步直连。
     */
    @Test
    void createPersonalAccountPushesUserToRealKeycloak() {
        String login = newLogin();
        String personId = seedPerson(empNo(1));
        String accountId = accounts.createPersonal(new AccountCreateCmd(personId, login, OPERATOR));

        // 未 flush 前：账号已落库，但 Keycloak 用户尚未创建，keycloak_user_id 为空
        assertThat(accounts.findById(accountId)).isPresent();
        assertThat(keycloak.findByUsername(login))
                .as("推送是异步的：未 flush 前 Keycloak 里不应有该用户")
                .isNull();
        assertThat(keycloakUserIdOf(accountId))
                .as("未 flush 前 keycloak_user_id 应为空")
                .isNull();

        flushPushes();

        KeycloakUserView created = keycloak.findByUsername(login);
        assertThat(created)
                .as("flush 后真实 Keycloak 里必须能查到该用户")
                .isNotNull();
        assertThat(created.enabled())
                .as("新开通账号在 Keycloak 侧应可登录")
                .isTrue();
        assertThat(keycloakUserIdOf(accountId))
                .as("推送成功后必须回写 keycloak_user_id，否则后续停用/改密/吊销会话都找不到人")
                .isEqualTo(created.id());
        assertThat(created.requiredActions())
                .as("开通时应带上配置的必需动作（默认首次改密 UPDATE_PASSWORD）")
                .contains("UPDATE_PASSWORD");
    }

    // ---------------------------------------------------------------- ② 停用后 Keycloak 登录失败

    /**
     * ② 停用后 Keycloak 侧不可登录（F-00 §6.1 第一段）。
     *
     * <p>断言真实 Keycloak 容器里该用户 {@code enabled=false}——这是「登录失败」在平台侧的可验证等价物。
     * **不做真实口令登录验证**，理由见类 javadoc（测试 realm 未开 password grant，
     * 改生产 realm 属安全配置变更，不擅自做）。
     *
     * <p>同时验证「停用连带吊销会话」：{@code disable()} 内部调 {@code revokeSessionsInternal}，
     * 对应 F-04 §5 / BAS-14 的动作链。这里通过**先停用、再重新开通**验证 {@code enabled} 能被
     * 平台再次改写，从而证明停用写入的是真实 Keycloak 状态而非仅本地库状态。
     */
    @Test
    void disabledAccountBecomesUnusableInKeycloak() {
        String login = newLogin();
        String accountId = accounts.createPersonal(new AccountCreateCmd(seedPerson(empNo(2)), login, OPERATOR));
        flushPushes();
        String keycloakUserId = requireKeycloakUserId(accountId);
        assertThat(keycloak.findUser(keycloakUserId).enabled())
                .as("前置：停用前 Keycloak 侧应可登录")
                .isTrue();

        accounts.disable(accountId, OPERATOR);
        flushPushes();

        assertThat(accounts.findById(accountId).orElseThrow().status())
                .as("Foundation 侧状态应置 DISABLED")
                .isEqualTo(AccountStatus.DISABLED);
        assertThat(keycloak.findUser(keycloakUserId).enabled())
                .as("停用后真实 Keycloak 侧必须 enabled=false（否则用户仍可登录）")
                .isFalse();

        // 反向验证：重新开通应把 Keycloak 侧改回可登录，证明上面的 false 不是「Keycloak 恒 false」的假象
        accounts.enable(accountId, OPERATOR);
        flushPushes();
        assertThat(keycloak.findUser(keycloakUserId).enabled())
                .as("重新开通后 Keycloak 侧应恢复可登录（证明停用确由平台驱动，而非 Keycloak 默认值）")
                .isTrue();
    }

    // ---------------------------------------------------------------- ③ 人为改 Keycloak 后对账发现并修复

    /**
     * ③ 人为改 Keycloak 后，每日对账发现差异并以 Foundation 为准回正（F-00 §6.1 第二段）。
     *
     * <p>「人为改动」用**真实 Keycloak Admin API** 制造（把 enabled 改成与 Foundation 相反），
     * 而非改内存快照——这样 {@code AccountReconcileJob} 必须真的从 Keycloak 分页拉全量
     * （{@code listUsers()}）、真的比对、真的回写，整条回路都被覆盖。
     *
     * <p>三类漂移各造一例（{@code ENABLED_MISMATCH} / {@code USERNAME_MISMATCH} /
     * {@code REQUIRED_ACTIONS_MISMATCH}），因为 {@code applyFix} 是 switch 分支，
     * 只测一类会让另外两类的修复路径处于零覆盖——而它们的修复动作各不相同
     * （{@code setEnabled} / {@code setUsername} / {@code setRequiredActions}）。
     */
    @Test
    void reconcileDetectsAndFixesManualKeycloakDrift() {
        // 三个账号，各制造一类漂移
        String driftEnabled = newLogin();
        String driftUsername = newLogin();
        String driftActions = newLogin();
        String enabledAccountId = accounts.createPersonal(
                new AccountCreateCmd(seedPerson(empNo(3)), driftEnabled, OPERATOR));
        String usernameAccountId = accounts.createPersonal(
                new AccountCreateCmd(seedPerson(empNo(4)), driftUsername, OPERATOR));
        String actionsAccountId = accounts.createPersonal(
                new AccountCreateCmd(seedPerson(empNo(5)), driftActions, OPERATOR));
        flushPushes();

        String enabledKcId = requireKeycloakUserId(enabledAccountId);
        String usernameKcId = requireKeycloakUserId(usernameAccountId);
        String actionsKcId = requireKeycloakUserId(actionsAccountId);

        // 人为改动 1：把可登录账号在 Keycloak 侧停用（Foundation 侧仍是 NORMAL）
        keycloak.setEnabled(enabledKcId, false);
        // 人为改动 2：把登录名改成别的（Foundation 侧仍是原登录名）
        String tamperedUsername = driftUsername + "-tampered";
        keycloak.setUsername(usernameKcId, tamperedUsername);
        // 人为改动 3：清空必需动作（Foundation 侧期望 UPDATE_PASSWORD）
        keycloak.setRequiredActions(actionsKcId, List.of());

        // 前置自证：漂移确实存在于真实 Keycloak，否则后面的「已修复」可能是「本来就没坏」的假绿
        assertThat(keycloak.findUser(enabledKcId).enabled())
                .as("前置：漂移 1 应已生效")
                .isFalse();
        assertThat(keycloak.findUser(usernameKcId).username())
                .as("前置：漂移 2 应已生效")
                .isEqualTo(tamperedUsername);
        assertThat(keycloak.findUser(actionsKcId).requiredActions())
                .as("前置：漂移 3 应已生效")
                .isEmpty();

        AccountReconcileRecord record = reconcileJob.reconcileOnce();

        assertThat(record)
                .as("必须取到单实例锁并完成一轮对账（返回 null 说明 Redis 锁被别人持有）")
                .isNotNull();
        assertThat(record.held())
                .as("本类已把 drift-threshold-percent 置 0，不应走「超阈值暂停修复」分支")
                .isFalse();
        assertThat(record.drifted())
                .as("三类人为漂移都应被发现")
                .isGreaterThanOrEqualTo(3);
        assertThat(record.fixed())
                .as("三类漂移都应被修复（MISSING_IN_KEYCLOAK 不可就地修，但本例没有该类漂移）")
                .isGreaterThanOrEqualTo(3);

        // 回正后与 Foundation 侧一致
        assertThat(keycloak.findUser(enabledKcId).enabled())
                .as("漂移 1 应被回正为可登录（以 Foundation 的 NORMAL 为准）")
                .isTrue();
        assertThat(keycloak.findUser(usernameKcId).username())
                .as("漂移 2 应被回正为 Foundation 侧登录名")
                .isEqualTo(driftUsername);
        assertThat(keycloak.findUser(actionsKcId).requiredActions())
                .as("漂移 3 应被回正为配置的必需动作")
                .contains("UPDATE_PASSWORD");
    }

    /**
     * ③ 的持久化：每轮对账都必须落一条 {@code fdn_account_reconcile} 记录（F-04 §6 的可审计要求）。
     *
     * <p>为什么单列一条用例：{@code AccountReconcileRecord} 是「对账发生过」的唯一审计凭证。
     * 若将来有人把 {@code reconciles.insert(record)} 漏掉，前一条用例仍会全绿
     * （修复动作照常发生），但运维就再也无法回答「昨晚对账跑了吗、修了几条」。
     * 这类「功能正常但可观测性静默丢失」的回归必须显式钉住。
     *
     * <p>无漂移时也应落记录（{@code drifted=0}），否则「没记录」与「跑了但没差异」无法区分。
     */
    @Test
    void eachReconcileRunPersistsAnAuditRecord() {
        String login = newLogin();
        accounts.createPersonal(new AccountCreateCmd(seedPerson(empNo(6)), login, OPERATOR));
        flushPushes();

        reconcileJob.reconcileOnce();
        reconcileJob.reconcileOnce();

        List<Integer> driftedCounts = jdbc.queryForList(
                "SELECT drifted FROM fdn_account_reconcile ORDER BY ran_at", Integer.class);
        assertThat(driftedCounts)
                .as("每轮对账都必须落一条记录（两轮即两条）")
                .hasSize(2);
        assertThat(driftedCounts)
                .as("无人为漂移时两轮都应报 0 差异")
                .containsOnly(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_account_reconcile WHERE held", Integer.class))
                .as("无漂移时不应判定为超阈值暂停")
                .isEqualTo(0);
    }

    // ---------------------------------------------------------------- 覆盖自证

    /**
     * 验收口径覆盖自证：F-00 §6.1 W82 点名的两段验收必须**各有对应的 @Test 方法**。
     *
     * <p>方法名在此**刻意硬编码**：这是有意的脆弱性——改名任一验收用例必须同步更新本清单，
     * 从而强迫改动者显式确认「这条验收口径仍被覆盖」（沿用 {@code GfPermissionGateIT} 同款惯例）。
     */
    @Test
    void allAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "createPersonalAccountPushesUserToRealKeycloak",      // 前提：推送链路接通
                "disabledAccountBecomesUnusableInKeycloak",           // ① 停用后 Keycloak 登录失败
                "reconcileDetectsAndFixesManualKeycloakDrift",        // ② 人为改动后对账发现并修复
                "eachReconcileRunPersistsAnAuditRecord");             // ② 的可审计落库

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-00 §6.1 W82 的验收口径必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }

    // ---------------------------------------------------------------- 造数与工具

    /**
     * 推送是异步的（{@code push} 只写待发记录），必须显式 flush 才会真调 Keycloak。
     *
     * <p><b>刻意直接调 {@code AccountPushService.flushDue}，而不经 {@code reconcileOnce()} 触发</b>：
     * 后者末尾虽也会 flush，但它同时会**追加一条对账记录**，那会让
     * {@link #eachReconcileRunPersistsAnAuditRecord()} 里「两轮对账 = 两条记录」的断言变成三条而失败，
     * 且失败信息（记录条数不对）完全指不到真因。limit 取足够大以覆盖本闸门造的记录。
     */
    private void flushPushes() {
        pushService.flushDue(200);
    }

    private String requireKeycloakUserId(String accountId) {
        String id = keycloakUserIdOf(accountId);
        assertThat(id).as("账号 %s 应已回写 keycloak_user_id", accountId).isNotBlank();
        return id;
    }

    private String keycloakUserIdOf(String accountId) {
        return jdbc.queryForObject("SELECT keycloak_user_id FROM fdn_account WHERE id = ?",
                String.class, accountId);
    }

    /** 登录名全局唯一且**不回收**（{@code uk_account_login_name}），故必须带唯一后缀。 */
    private String newLogin() {
        return LOGIN_PREFIX + Ids.newId(OrgIdPrefix.PERSON).substring(4, 14).toLowerCase();
    }

    /** 工号唯一（{@code UNIQUE (emp_no)}），故带序号 + 随机后缀。 */
    private String empNo(int seq) {
        return EMP_PREFIX + seq + Ids.newId(OrgIdPrefix.PERSON).substring(4, 10);
    }

    private String seedPerson(String empNo) {
        String id = Ids.newId(OrgIdPrefix.PERSON);
        // 手机 / 邮箱列均可空；账号开通只用 empNo 生成登录名、用 name/email 填 Keycloak 表示
        persons.insert(new Person(id, empNo, "闸门账号人员-" + empNo, PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, null, null, "local", null, 0L));
        return id;
    }
}
