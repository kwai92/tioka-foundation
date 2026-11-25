package com.foundation.server.gate;

import com.foundation.account.api.AccountApi;
import com.foundation.account.api.dto.AccountCreateCmd;
import com.foundation.account.api.dto.AccountStatus;
import com.foundation.account.api.dto.OffboardingResult;
import com.foundation.account.api.dto.ServiceAccountCreateCmd;
import com.foundation.account.application.OffboardingApplicationService;
import com.foundation.app.api.AppApi;
import com.foundation.app.api.AppRegistrationCmd;
import com.foundation.common.api.keycloak.KeycloakOidcClientAdmin;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1 W85，F-04 BAS-14 §1/§3）：人员离职联动在**真实 Keycloak** 上按动作链成立。
 *
 * <p>验收口径（F-11 §6.1 / F-00 §6.1）：停用后两边会话 1 分钟内失效；交接待办生成；记忆清理触发。
 *
 * <p><b>本闸门的覆盖范围（部分形态，如实标注）</b>：该口径的完整形态需要「已登录 Tioka 与合同业务系统的
 * 在职人员」作为前置，而这两个接入方在当前仓库都不存在（合同业务系统全仓无命中；Tioka 属另一仓且硬边界
 * 不动其业务行为）。因此本闸门覆盖 **Foundation 侧能独立成立的动作链**，即 F-04 BAS-14 §1 的四步编排：
 * <ol>
 *   <li>账号停用：{@code fdn_account.status=DISABLED} 且真实 Keycloak 侧 {@code enabled=false}；</li>
 *   <li>会话吊销：{@code OffboardingResult.sessionsRevoked=true} 且 {@code revokedAt} 非空；</li>
 *   <li>交接待办生成：{@code handoverCount} 与 {@code fdn_handover_todo} 落库行数一致、状态 PENDING；</li>
 *   <li>离职事件：{@code fdn.person.disabled} 落 {@code fdn_outbox}，person 状态置 {@code LEFT}。</li>
 * </ol>
 *
 * <p><b>明确不在本闸门范围（不得视为已覆盖）</b>：
 * <ul>
 *   <li>「两边会话 1 分钟内失效」中的<b>「两边」</b>指 Tioka 与合同业务系统的会话失效，需两侧消费
 *       {@code fdn.account.sessions_revoked} 后各自吊销本地令牌，属 F-11 的 {@code TiokaOffboardingIT}
 *       与 {@code GfFoundationE2EIT}。本闸门只验到<b>平台侧已发出吊销</b>（Keycloak {@code logoutSessions}
 *       被调用、事件已落 outbox），不验接入方侧的会话真的失效。</li>
 *   <li>「记忆清理触发」属 <b>MEM-01</b>，是 Tioka 侧能力（消费 {@code fdn.person.disabled} 后清理记忆），
 *       Foundation 全仓无 memory 概念。本闸门只验到<b>触发它的事件已发出</b>，不验记忆被清理。</li>
 * </ul>
 * 这两点按判据③<b>如实标注而非用 {@code @Disabled} 掩盖</b>：它们不是「暂时关掉的用例」，
 * 而是「本闸门的边界之外、由别的闸门承担」。
 *
 * <p><b>「1 分钟内」如何对待</b>：{@code OffboardingApplicationService.warnIfBeyondDeadline} 只在超时打 WARN、
 * <b>不抛异常</b>，故生产不会因超时失败。本闸门<b>不</b>用真实计时去断言「≤60 秒」——那样会得到一个
 * 依赖机器性能的脆弱断言，且闸门内 Keycloak 调用本就毫秒级、测不出真实生产时延。改为断言
 * <b>{@code revokedAt} 非空且不早于动作开始</b>（即吊销确实发生并被记录时间），把「时延口径」留给
 * F-11 §7 的生产观察项（其表明确列「离职会话失效 ≤60 秒」为线上指标）。
 *
 * <p><b>本闸门验什么而既有单测验不到</b>：{@code OffboardingChainTest} 用内存 stub 验<b>编排顺序</b>
 * （各步调用次序、失败重试、{@code revokeOnDisable=false} 分支）。本闸门跑完整
 * {@code FoundationApplication} + PostgreSQL + 真实 Keycloak，验 stub 碰不到的三层：
 * <ul>
 *   <li>动作链<b>跨三个域</b>（account / app / org）在真实事务边界下的最终状态是否自洽——
 *       {@code OffboardingApplicationService} javadoc 明写「本类自身不开事务，每步委托各自的事务服务，
 *       后续步骤失败不回滚已提交的会话吊销」，这个「部分提交」语义只有真库才测得出；</li>
 *   <li>Keycloak 侧用户是否<b>真的</b>被停用（{@code enabled=false}），而非只在本地库置 DISABLED；</li>
 *   <li>离职事件是否<b>真的</b>落到 {@code fdn_outbox}（含 payload），即「与业务落库同事务」的实际兑现。</li>
 * </ul>
 *
 * <p><b>清理口径（gate job 共享 static PG + Keycloak 单例）</b>：
 * <ul>
 *   <li>本地表按本闸门前缀 DELETE，<b>绝不 TRUNCATE</b>：{@code fdn_app} 有 V3/V4 种子、
 *       {@code fdn_person} 被多个闸门共享。{@code fdn_handover_todo} 无外键（已核实 V14），
 *       但 {@code fdn_account_push.account_id} 有 {@code REFERENCES fdn_account(id)}，故先删 push 再删 account。</li>
 *   <li><b>不清 {@code fdn_outbox}</b>：跨域共享表，既有闸门都刻意不碰。本闸门改用
 *       {@code aggregate=personId}（{@code Ids.newId} 生成的 ULID，跨闸门与跨运行都不会撞）精确过滤，
 *       既读到自己的事件又不干扰别人。</li>
 *   <li>Keycloak：{@code createPersonal} 建的用户与 {@code createServiceAccount} 建的
 *       {@code {appCode}-sa} 客户端都要在 {@link #cleanUpKeycloak()} 删除，否则残留给同批次闸门。
 *       用「登记前即记入待删列表」的写法，即使中途失败也能清理。</li>
 * </ul>
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest
class GfOffboardingGateIT {

    /** 本闸门专属应用编码（登记服务账号需要一个已存在的 app）。 */
    private static final String APP_CODE = "gfoffboarding-app";
    /** 本闸门自建数据的统一前缀，清理时据此精确 DELETE。 */
    private static final String EMP_PREFIX = "O0";
    private static final String SA_NAME_PREFIX = "gfoffboarding-sa";
    private static final String OPERATOR = "gate";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
        FoundationContainers.registerKeycloak(registry);
    }

    @Autowired
    private OffboardingApplicationService offboarding;

    @Autowired
    private AccountApi accounts;

    @Autowired
    private AppApi apps;

    /** 真实 Keycloak 用户管理，用于断言停用效果与清理。 */
    @Autowired
    private KeycloakUserAdmin keycloakUsers;

    /** 真实 Keycloak OIDC 客户端管理，仅用于清理服务账号客户端。 */
    @Autowired
    private KeycloakOidcClientAdmin keycloakClients;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private JdbcTemplate jdbc;

    /** 待删的 Keycloak 用户名（个人账号）与 clientId（服务账号客户端），登记即入列以便异常时也能清理。 */
    private final List<String> keycloakUsernames = new ArrayList<>();
    private final List<String> keycloakClientIds = new ArrayList<>();

    @BeforeEach
    void cleanUp() {
        cleanUpKeycloak();
        // fdn_handover_todo 无外键；account_push 有 REFERENCES fdn_account(id)，故先删 push 再删 account
        jdbc.update("DELETE FROM fdn_handover_todo WHERE owner_person_id IN "
                + "(SELECT id FROM fdn_person WHERE emp_no LIKE ?)", EMP_PREFIX + "%");
        // 服务账号的 login_name 由 uniqueLoginName(appCode, name) 生成（形如
        // "gfoffboarding-app.gfoffboarding-sa-1"），**不带 EMP_PREFIX**，故必须同时按
        // APP_CODE 前缀清理，否则其 fdn_account / fdn_account_push 行会残留在 gate 共享库里。
        jdbc.update("DELETE FROM fdn_account_push WHERE account_id IN "
                + "(SELECT id FROM fdn_account WHERE login_name LIKE ? OR login_name LIKE ?)",
                EMP_PREFIX + "%", APP_CODE + ".%");
        jdbc.update("DELETE FROM fdn_account WHERE login_name LIKE ? OR login_name LIKE ?",
                EMP_PREFIX + "%", APP_CODE + ".%");
        jdbc.update("DELETE FROM fdn_service_account WHERE name LIKE ?", SA_NAME_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_app_client WHERE app_id IN (SELECT id FROM fdn_app WHERE code = ?)", APP_CODE);
        jdbc.update("DELETE FROM fdn_app WHERE code = ?", APP_CODE);
        jdbc.update("DELETE FROM fdn_person WHERE emp_no LIKE ?", EMP_PREFIX + "%");
    }

    @AfterEach
    void cleanUpKeycloak() {
        // 共享 Keycloak：本闸门建的用户与客户端必须删掉，否则残留给同批次其它闸门
        for (String username : keycloakUsernames) {
            KeycloakUserView user = keycloakUsers.findByUsername(username);
            if (user != null) {
                keycloakUsers.deleteUser(user.id());
            }
        }
        for (String clientId : keycloakClientIds) {
            keycloakClients.delete(clientId);
        }
        keycloakUsernames.clear();
        keycloakClientIds.clear();
    }

    // ---------------------------------------------------------------- ① 停用与会话吊销

    /**
     * ① 离职后账号停用 + Keycloak 侧真被停用 + 会话吊销已记录（F-04 §3 动作链第 1、2 步）。
     *
     * <p>断言三处一致，缺一都可能掩盖缺陷：
     * <ul>
     *   <li><b>本地库</b>：{@code fdn_account.status=DISABLED}；</li>
     *   <li><b>真实 Keycloak</b>：该用户 {@code enabled=false}——只断本地库会漏掉「库置了状态但没推到
     *       Keycloak」这类半接线缺陷（用户实际仍能登录）；</li>
     *   <li><b>返回值</b>：{@code sessionsRevoked=true} 且 {@code revokedAt} 非空、不早于动作开始，
     *       且 {@code keycloakUserId} 已带出（供接入方定位要吊销谁的会话）。</li>
     * </ul>
     * 「1 分钟内」的时延口径不在本闸门断言，理由见类 javadoc（生产只 WARN 不抛，且闸门内测不出真实时延）。
     */
    @Test
    void offboardDisablesAccountInBothDatabaseAndKeycloak() {
        apps.register(new AppRegistrationCmd(APP_CODE, "闸门离职应用", null, "https://gfoff.example.com",
                OPERATOR, "SUB", List.of("https://gfoff.example.com/callback"), OPERATOR));
        String personId = seedPerson(empNo(1));
        String login = loginName(1);
        keycloakUsernames.add(login);
        String accountId = accounts.createPersonal(new AccountCreateCmd(personId, login, OPERATOR));
        String keycloakUserId = jdbc.queryForObject(
                "SELECT keycloak_user_id FROM fdn_account WHERE id = ?", String.class, accountId);
        assertThat(keycloakUserId).as("前置：个人账号应已推送到 Keycloak 并回写 ID").isNotBlank();
        assertThat(keycloakUsers.findUser(keycloakUserId).enabled())
                .as("前置：离职前 Keycloak 侧应可登录")
                .isTrue();

        OffboardingResult result = offboarding.offboard(personId, OPERATOR);

        assertThat(result.personId()).isEqualTo(personId);
        assertThat(result.accountId())
                .as("应带出被停用的个人账号 ID")
                .isEqualTo(accountId);
        assertThat(result.keycloakUserId())
                .as("应带出 Keycloak 用户 ID（接入方据此吊销本地会话）")
                .isEqualTo(keycloakUserId);
        assertThat(accounts.findById(accountId).orElseThrow().status())
                .as("Foundation 侧账号状态应置 DISABLED")
                .isEqualTo(AccountStatus.DISABLED);
        assertThat(keycloakUsers.findUser(keycloakUserId).enabled())
                .as("真实 Keycloak 侧必须 enabled=false，否则用户实际仍能登录（半接线缺陷）")
                .isFalse();
        assertThat(result.sessionsRevoked())
                .as("revoke-on-disable 默认开启，应报告会话已吊销")
                .isTrue();
        assertThat(result.revokedAt())
                .as("会话吊销时间应被记录（F-04 §3 的 1 分钟口径核验依据）")
                .isNotNull();
        assertThat(persons.findById(personId).orElseThrow().status())
                .as("人员状态应置 LEFT")
                .isEqualTo(PersonStatus.LEFT);
    }

    // ---------------------------------------------------------------- ② 交接待办生成

    /**
     * ② 离职人员负责的服务账号进入交接待办（F-04 §3「交接待办生成」、BAS-14 §1 第 3 步）。
     *
     * <p>造两个由<b>同一人</b>负责的服务账号，断言 {@code handoverCount=2} 且
     * {@code fdn_handover_todo} 落库 2 行、均为 {@code PENDING}。刻意造两个而非一个：
     * 只造一个时「计数=1」与「只处理了第一条就返回」无法区分，两条才能证明遍历完整。
     *
     * <p>同时断言待办的 {@code owner_person_id} 与 {@code app_code} 正确——
     * {@code HandoverService.enqueue} 是<b>遍历全部 app 的全部服务账号</b>按 owner 匹配，
     * 若匹配条件写错就会把别人的服务账号也拉进来（gate job 共享库，这类串号会静默发生）。
     */
    @Test
    void offboardEnqueuesHandoverTodosForOwnedServiceAccounts() {
        apps.register(new AppRegistrationCmd(APP_CODE, "闸门离职应用", null, "https://gfoff.example.com",
                OPERATOR, "SUB", List.of("https://gfoff.example.com/callback"), OPERATOR));
        String personId = seedPerson(empNo(2));
        keycloakClientIds.add(APP_CODE + "-sa");

        accounts.createServiceAccount(new ServiceAccountCreateCmd(APP_CODE, SA_NAME_PREFIX + "-1",
                List.of(), personId, null, OPERATOR));
        accounts.createServiceAccount(new ServiceAccountCreateCmd(APP_CODE, SA_NAME_PREFIX + "-2",
                List.of(), personId, null, OPERATOR));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_service_account WHERE name LIKE ?",
                Integer.class, SA_NAME_PREFIX + "%"))
                .as("前置：两个服务账号应已登记")
                .isEqualTo(2);

        OffboardingResult result = offboarding.offboard(personId, OPERATOR);

        assertThat(result.handoverCount())
                .as("该人负责的两个服务账号都应进入交接待办（只造一个无法区分「计数对」与「只处理第一条」）")
                .isEqualTo(2);
        List<Map<String, Object>> todos = jdbc.queryForList(
                "SELECT owner_person_id, app_code, status FROM fdn_handover_todo WHERE owner_person_id = ?",
                personId);
        assertThat(todos)
                .as("fdn_handover_todo 落库行数应与 handoverCount 一致")
                .hasSize(2);
        assertThat(todos)
                .as("待办必须归属离职人员本人、且来自本闸门应用（enqueue 遍历全部 app，匹配写错会串号）")
                .allSatisfy(todo -> {
                    assertThat(todo.get("owner_person_id")).isEqualTo(personId);
                    assertThat(todo.get("app_code")).isEqualTo(APP_CODE);
                    assertThat(todo.get("status")).isEqualTo("PENDING");
                });
    }

    // ---------------------------------------------------------------- ③ 离职事件落 outbox

    /**
     * ③ 离职动作发出 {@code fdn.person.disabled}（F-04 §3 事件口径、F-03 §6 契约）。
     *
     * <p>这是「记忆清理触发」与「Tioka 侧吊销本地令牌」两条下游口径的<b>唯一平台侧前提</b>：
     * 接入方靠消费该事件行动。本闸门断言事件<b>已落 outbox 可被投递</b>（同事务写入），
     * 而<b>不</b>断言下游真的清理了记忆或吊销了 Tioka 令牌——那属 MEM-01 与 {@code TiokaOffboardingIT}。
     *
     * <p>同时断言 {@code fdn.account.sessions_revoked} 也已落库：账号停用连带吊销会话时发出，
     * 是接入方「收到即吊销本地令牌」的依据（F-11 §5 第 7 条）。两个事件缺任一个，
     * 下游的离职联动就断链，故都必须钉住。
     *
     * <p>按 {@code aggregate=personId} 精确过滤（见类 javadoc 清理口径），避免读到别的闸门的事件。
     */
    @Test
    void offboardEmitsPersonDisabledAndSessionsRevokedEvents() {
        apps.register(new AppRegistrationCmd(APP_CODE, "闸门离职应用", null, "https://gfoff.example.com",
                OPERATOR, "SUB", List.of("https://gfoff.example.com/callback"), OPERATOR));
        String personId = seedPerson(empNo(3));
        String login = loginName(3);
        keycloakUsernames.add(login);
        accounts.createPersonal(new AccountCreateCmd(personId, login, OPERATOR));

        offboarding.offboard(personId, OPERATOR);

        List<String> eventTypes = jdbc.queryForList(
                "SELECT event_type FROM fdn_outbox WHERE aggregate = ? ORDER BY create_at",
                String.class, personId);
        assertThat(eventTypes)
                .as("离职必须发出 fdn.person.disabled（下游记忆清理与 Tioka 令牌吊销的唯一触发源）")
                .contains("fdn.person.disabled");
        assertThat(eventTypes)
                .as("账号停用连带吊销会话必须发出 fdn.account.sessions_revoked（接入方据此吊销本地令牌）")
                .contains("fdn.account.sessions_revoked");

        // 事件体经 JSONB 往返后仍带得出 personId：接入方要靠它定位是谁离职，写错则下游静默不生效
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM fdn_outbox WHERE aggregate = ? AND event_type = 'fdn.person.disabled'",
                String.class, personId);
        assertThat(payload)
                .as("fdn.person.disabled 的事件体必须含 personId，否则消费方无法定位离职人员")
                .contains(personId);
    }

    // ---------------------------------------------------------------- 覆盖自证

    /**
     * 验收口径覆盖自证：F-04 §3 的三条平台侧验收必须**各有对应的 @Test 方法**。
     *
     * <p>方法名在此**刻意硬编码**：删改任一验收用例即红，防「闸门已建」与「验收点已覆盖」被混为一谈
     * （沿用 {@code GfPermissionGateIT} / {@code GfSsoGateIT} 同款惯例）。
     *
     * <p>注意清单<b>不含</b>「两边会话失效」与「记忆清理触发」——它们在本闸门边界之外
     * （见类 javadoc），列入会让自证用例给出虚假的「已覆盖」信号。
     */
    @Test
    void allPlatformSideAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "offboardDisablesAccountInBothDatabaseAndKeycloak",     // ① 停用 + Keycloak 停用 + 会话吊销
                "offboardEnqueuesHandoverTodosForOwnedServiceAccounts", // ② 交接待办生成
                "offboardEmitsPersonDisabledAndSessionsRevokedEvents"); // ③ 离职事件落 outbox

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-04 §3 的平台侧验收点必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }

    // ---------------------------------------------------------------- 造数

    /** 登录名全局唯一且不回收，故用完整 ULID 随机位（前缀子串是毫秒时间戳，紧密循环内会重复）。 */
    private String loginName(int seq) {
        return EMP_PREFIX + seq + Ids.newId(OrgIdPrefix.PERSON).substring(4).toLowerCase();
    }

    /** 工号唯一（{@code UNIQUE (emp_no)}），同样用完整 ULID 随机位。 */
    private String empNo(int seq) {
        return EMP_PREFIX + seq + Ids.newId(OrgIdPrefix.PERSON).substring(4).toLowerCase();
    }

    private String seedPerson(String empNo) {
        String id = Ids.newId(OrgIdPrefix.PERSON);
        // 手机/邮箱列均可空；离职链路只用 empNo 生成登录名、用 name 填 Keycloak 表示
        persons.insert(new Person(id, empNo, "闸门离职人员-" + empNo, PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, null, null, "local", null, 0L));
        return id;
    }
}
