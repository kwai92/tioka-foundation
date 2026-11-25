package com.foundation.server.gate;

import com.foundation.app.domain.App;
import com.foundation.app.infrastructure.AppRepository;
import com.foundation.authz.api.AuthzChangedEvent;
import com.foundation.authz.api.AuthzSnapshotView;
import com.foundation.authz.api.GrantApi;
import com.foundation.authz.api.RoleApi;
import com.foundation.authz.api.RoleCmd;
import com.foundation.authz.api.RoleGrantCmd;
import com.foundation.authz.api.SnapshotApi;
import com.foundation.authz.api.SnapshotRecomputeCmd;
import com.foundation.common.api.event.EventProperties;
import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.PersonRepository;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1 W84，BAS-07）：授权快照的版本递增与变更事件在真实库上的端到端成立。
 *
 * <p>验收口径（F-00 §6.1）：授权变更后 5 秒内事件发出、快照版本递增，starter 缓存失效。
 *
 * <p><b>本闸门覆盖哪一段，以及为什么</b>：该口径跨三段——① 服务端重算并写快照（版本递增）、
 * ② 同事务把 {@code fdn.authz.changed} 写入 {@code fdn_outbox}、③ starter 侧消费事件失效本地缓存。
 * 本闸门覆盖 ①②（服务端，真库真事务）；③ 属接入侧，由 {@code AuthorizationSnapshotInvalidationListenerTest}
 * 的 5 个用例覆盖（MQ 事件体 / Webhook 信封 / 跨应用忽略 / 非授权事件忽略 / 非法事件不静默吞）。
 * 之所以不在本类端到端跑 ③：{@code foundation-server} **不依赖** {@code foundation-starter-client}
 * （分层必然——服务端不该依赖接入方 starter），强行引入会把服务端与某个接入形态绑死。
 * 两段合起来才等于验收口径全文，缺任一段都不成立，故分工必须显式写明而非默认「都测了」。
 *
 * <p><b>与既有单测的分工</b>：{@code SnapshotRecomputeService} 的版本递增与
 * {@code AuthzChangedEventPublisher} 的 payload 组装都有内存 stub 单测验**算法**；本闸门跑完整
 * {@code FoundationApplication} + PostgreSQL 容器，验的是算法之下那层单测碰不到的东西：
 * <ul>
 *   <li>{@code SnapshotRecomputeService.recompute} 的 {@code @Transactional} 是否真把「写快照」与
 *       「写 outbox」绑在同一事务——这是 S15 §14.2「与业务落库同事务」的实际兑现，
 *       stub 测不出事务边界，只能靠真库同时查到两行来证明；</li>
 *   <li>{@code fdn_authz_snapshot} 的 {@code UNIQUE (person_id, app_code)} 与 {@code version} 列
 *       在真表 DDL 上是否支撑「同一 (人员, 应用) 版本单调递增」（而非插入新行或重置为 1）；</li>
 *   <li>{@code fdn_outbox.payload} 是 JSONB：事件体经 MyBatis 写入再读出后，
 *       {@code snapshotVersion} / {@code personId} / {@code appCode} / {@code changeType} 是否仍完好
 *       （JSONB 与 Java Map 的往返一致性是 stub 永远测不到的一环）。</li>
 * </ul>
 *
 * <p><b>关于「5 秒内发出」的如实标注</b>：gate profile 只经 {@code FoundationContainers.register}
 * 注册 PostgreSQL 与 Redis，**RocketMQ 是懒启动**（{@code RocketMqCluster} 不被 register 触发），
 * 且 outbox→MQ 的中继不在 gate 装配内。故本闸门断言的是**事件已在同一事务内落库可被投递**
 * （{@code fdn_outbox} 有对应行、{@code status='NEW'}），而非「已投递到 broker」。
 * 「5 秒」是中继的投递时限口径，属 MQ 中继与 {@code GaEventOutboxGateIT} 一类闸门的职责范围；
 * 把它硬塞进本闸门只会得到一个依赖 broker 启动的慢且脆的断言。**不夸大覆盖范围**：
 * 本类不声称验证了投递时限。
 *
 * <p><b>关于「授权变更后」的触发方式</b>：实测 {@code RoleGrantService.grant} 只落库授权、
 * **不触发快照重算**，全仓也没有任何事件监听器把授权变更接到 {@code recomputeForGrant}
 * （重算入口只有 {@code SnapshotController} 的 {@code POST /api/fdn/snapshots:recompute} 与
 * {@code SnapshotAppService.find} 的按需回填）。即「变更后何时重算」当前是**显式触发**而非自动。
 * 本闸门据此走平台公开的 {@link SnapshotApi#recompute} 显式重算，再验证「重算→版本递增→事件落库」
 * 这条真实链路；**不**通过手工拼造数据来假装它是自动发生的。若将来接线了自动重算，
 * 应新增一条用例断言「grant 之后无需显式调用即产生新版本」，而非修改本类语义。
 *
 * <p><b>清理口径</b>：gate job 内所有闸门共享同一个 PostgreSQL 容器（{@code FoundationContainers.PG}
 * 是 static 单例，surefire 默认 forkCount=1 / reuseForks=true → 同一 JVM、Flyway 只跑一次、种子只种一次）。
 * 故：① 对**有迁移种子**的表（{@code fdn_app}/{@code fdn_role} 等）只能按本闸门前缀 DELETE，绝不能 TRUNCATE
 * （否则会清掉 V3/V4 的应用与三员角色种子，使同批次其它闸门必败）；
 * ② {@code fdn_outbox} 是**跨域共享表**，既有闸门（{@code GfDataScopeGateIT} / {@code GfOrgImportGateIT} /
 * {@code GfPermissionGateIT}）都刻意不碰它，本闸门同样不 TRUNCATE——而是用**本闸门专属的 personId**
 * （{@code Ids.newId} 生成的 ULID，全局唯一）作为 {@code aggregate} 精确过滤，既读到自己的事件，
 * 又不干扰别的闸门。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest
class GfSnapshotEventGateIT {

    /** 本闸门自建数据的统一前缀，清理时据此精确 DELETE（不碰迁移种子）。 */
    private static final String ROLE_PREFIX = "gate.role.snapshot.";
    private static final String APP_ID = "app_gate_snapshot";
    /** 应用编码须匹配 {@code ^[a-z][a-z0-9-]{1,31}$}（{@code AppRegistrationCmd} 的口径），故不含点号。 */
    private static final String APP_CODE = "gate-snapshot-app";
    private static final String OPERATOR = "gate";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    @Autowired
    private SnapshotApi snapshots;

    @Autowired
    private GrantApi grants;

    @Autowired
    private RoleApi roles;

    @Autowired
    private AppRepository apps;

    @Autowired
    private PersonRepository persons;

    /** 事件主题由平台配置给出，避免测试硬编码 {@code fdn-events} 与生产口径漂移。 */
    @Autowired
    private EventProperties eventProperties;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        // 逆外键依赖顺序清理，且**只碰本闸门自己的数据**：
        // fdn_authz_snapshot / fdn_role_grant / fdn_person 由其它闸门共享，故按本闸门标识 DELETE 而非 TRUNCATE。
        // 注意 fdn_outbox 刻意不清理（跨域共享表，见类 javadoc「清理口径」）。
        //
        // 快照按 **person_id 关联删除**而非按 app_code：隔离性用例会额外在种子应用 foundation 上重算一次
        // （用于证明版本按 (人员, 应用) 独立），若只按 APP_CODE 删就会把那一行孤儿快照留在共享容器里。
        // 必须先删快照再删人员，因为下面要按 fdn_person.emp_no 反查 person_id。
        jdbc.update("DELETE FROM fdn_authz_snapshot WHERE person_id IN "
                + "(SELECT id FROM fdn_person WHERE emp_no LIKE 'S0%')");
        jdbc.update("DELETE FROM fdn_role_grant WHERE role_code LIKE ?", ROLE_PREFIX + "%");
        // fdn_role_grant.role_code 有 REFERENCES fdn_role(code)，故先删授权再删角色
        jdbc.update("DELETE FROM fdn_role WHERE code LIKE ?", ROLE_PREFIX + "%");
        jdbc.update("DELETE FROM fdn_app WHERE code = ?", APP_CODE);
        // 人员行按本闸门工号前缀清理（S0 前缀不与其它闸门撞：GfOrgImportGateIT 用 E0001、
        // GfPermissionGateIT 用 P0xxx、GfDataScopeGateIT 用 A/B/C/D000x）。
        // 本闸门不建任职/汇报线/外部身份，故 fdn_person 上不会有 FK 引用者残留。
        jdbc.update("DELETE FROM fdn_person WHERE emp_no LIKE 'S0%'");
    }

    // ---------------------------------------------------------------- 前置与清理自证

    /**
     * 清理不得破坏迁移种子（V3/V4 的应用、V4 的三员角色与互斥对）。
     *
     * <p>{@code cleanUp()} 是 {@code @BeforeEach}，已在本用例前执行过，故此处直接断言种子仍在。
     * 这条断言把「gate job 内闸门共享同一 PG 容器、种子只种一次」这一隐含约束变成可自动判定的信号：
     * 若有人把清理改成 TRUNCATE 那几张有种子的表，本用例会红，而不是等同批次其它闸门静默失败后才发现。
     */
    @Test
    void cleanUpPreservesSeedData() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_app WHERE code IN ('portal','admin','foundation')", Integer.class))
                .as("V3/V4 种入的 3 个平台应用必须在清理后仍在")
                .isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM fdn_role WHERE code IN ('system_admin','security_admin','audit_admin',"
                        + "'org_admin','app_admin')", Integer.class))
                .as("5 个内置角色种子必须在清理后仍在")
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_role_mutex", Integer.class))
                .as("三员互斥对种子（3 对）必须在清理后仍在")
                .isEqualTo(3);
    }

    // ---------------------------------------------------------------- ① 快照版本递增

    /**
     * ① 快照版本递增：同一 {@code (personId, appCode)} 连续重算，版本必须 1 → 2 → 3 单调递增，
     * 且**始终只有一行**（受 {@code UNIQUE (person_id, app_code)} 约束走 upsert，而非插入新行）。
     *
     * <p>为什么必须断言「只有一行」：若唯一索引缺失或 upsert 退化成 insert，版本序列仍可能
     * 看起来递增（每次读到最新一行），但快照表会无限膨胀、且 {@code find} 读到哪一行取决于查询顺序——
     * 这种退化在弱断言下会假绿，故把行数做成独立信号。
     */
    @Test
    void snapshotVersionIncrementsMonotonicallyPerPersonAndApp() {
        seedApp();
        String roleCode = seedSystemRole(ROLE_PREFIX + "v1");
        String personId = seedPerson("S0100");
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personId, false,
                null, null, "MANUAL", OPERATOR));

        // 首次重算：版本从 0 起算 +1 = 1
        AuthzSnapshotView first = recomputeAndFind(personId);
        assertThat(first.version()).as("首次重算后版本应为 1").isEqualTo(1L);
        assertThat(first.appCode()).as("快照应归属本闸门应用").isEqualTo(APP_CODE);

        // 第二次重算：版本递增而非重置
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personId)));
        AuthzSnapshotView second = snapshots.find(APP_CODE, personId).orElseThrow();
        assertThat(second.version()).as("第二次重算后版本应递增为 2").isEqualTo(2L);

        // 第三次重算：继续递增
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personId)));
        AuthzSnapshotView third = snapshots.find(APP_CODE, personId).orElseThrow();
        assertThat(third.version()).as("第三次重算后版本应递增为 3").isEqualTo(3L);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_authz_snapshot "
                        + "WHERE person_id = ? AND app_code = ?", Integer.class, personId, APP_CODE))
                .as("同一 (人员, 应用) 必须只有一行快照（唯一索引 + upsert），不得随行数膨胀")
                .isEqualTo(1);
    }

    /**
     * ① 的隔离性：版本按 {@code (personId, appCode)} 各自独立递增，互不串号。
     *
     * <p>{@code AuthorizationSnapshot} 的 javadoc 明确「version 按 (personId, appCode) 单调递增」。
     * 若实现误用全局序列（或按 personId 单键），本用例会红——而单键递增在单人员用例里看不出来。
     */
    @Test
    void snapshotVersionsAreIndependentAcrossPersonsAndApps() {
        seedApp();
        String roleCode = seedSystemRole(ROLE_PREFIX + "iso");
        String personA = seedPerson("S0200");
        String personB = seedPerson("S0201");
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personA, false, null, null, "MANUAL", OPERATOR));
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personB, false, null, null, "MANUAL", OPERATOR));

        // A 重算三次，B 只重算一次：两者版本必须各自独立
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personA)));
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personA)));
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personA)));
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personB)));

        assertThat(snapshots.version(APP_CODE, personA)).contains(3L);
        assertThat(snapshots.version(APP_CODE, personB))
                .as("B 的版本不得被 A 的重算带动（否则说明用了全局序列）")
                .contains(1L);

        // 同一人在另一个应用（平台种子应用 foundation）上的版本也从 1 起算，不继承本应用的 3
        snapshots.recompute(new SnapshotRecomputeCmd("foundation", List.of(personA)));
        assertThat(snapshots.version("foundation", personA))
                .as("同一人在不同应用上的版本必须各自独立")
                .contains(1L);
        assertThat(snapshots.version(APP_CODE, personA))
                .as("在其它应用重算不得影响本应用的版本")
                .contains(3L);
    }

    // ---------------------------------------------------------------- ② 变更事件落库

    /**
     * ② 授权变更后事件与快照同事务落库：重算后 {@code fdn_outbox} 必须有一条
     * {@code fdn.authz.changed}，且 {@code aggregate} 是该人员、{@code topic} 是平台配置的主题。
     *
     * <p><b>同事务的证据</b>：{@code recompute} 带 {@code @Transactional}，内部先 {@code snapshots.upsert}
     * 再 {@code publisher.publish}。本用例在**同一线程、调用返回后**同时查到快照行与事件行——
     * 若两者不在同一事务（例如 publisher 另起事务或异步），在真库上就可能出现
     * 「快照已写而事件未写」的中间态被读到。故本用例先断言两者同时可见，再由
     * {@link #eventPayloadSurvivesJsonbRoundTrip()} 断言事件内容完好。
     */
    @Test
    void recomputeWritesAuthzChangedEventToOutboxInSameTransaction() {
        seedApp();
        String roleCode = seedSystemRole(ROLE_PREFIX + "evt");
        String personId = seedPerson("S0300");
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personId, false, null, null, "MANUAL", OPERATOR));

        AuthzSnapshotView snapshot = recomputeAndFind(personId);

        List<Map<String, Object>> events = outboxEventsFor(personId);
        assertThat(events)
                .as("重算后必须有一条 fdn.authz.changed 事件落 outbox（与快照同事务）")
                .hasSize(1);

        Map<String, Object> event = events.get(0);
        assertThat(event.get("topic"))
                .as("事件主题应取平台配置值而非硬编码")
                .isEqualTo(eventProperties.getTopic());
        assertThat(event.get("event_type")).isEqualTo(AuthzChangedEvent.EVENT_TYPE);
        assertThat(event.get("aggregate"))
                .as("聚合标识应是被变更人员（据此可精确过滤，不必碰共享 outbox 表）")
                .isEqualTo(personId);
        assertThat(event.get("status"))
                .as("gate profile 不启动 MQ 中继，事件应停在待投递态")
                .isEqualTo("NEW");

        // 快照行与事件行同时可见，即「同事务」在真库上的可观测结果
        assertThat(jdbc.queryForObject("SELECT count(*) FROM fdn_authz_snapshot "
                        + "WHERE person_id = ? AND app_code = ?", Integer.class, personId, APP_CODE))
                .as("事件落库时快照必须已落库（同事务，不得出现只写其一的中间态）")
                .isEqualTo(1);
        assertThat(snapshot.version()).isEqualTo(1L);
    }

    /**
     * ② 的事件体经 JSONB 往返后仍完好，且携带的版本与快照一致。
     *
     * <p>{@code fdn_outbox.payload} 是 JSONB 列：{@code AuthzChangedEventPublisher} 用
     * {@code LinkedHashMap} 组装事件体，经 MyBatis 写入再读出，中间要过 Jackson 序列化与
     * PG 的 JSONB 解析。这一段是**内存 stub 永远测不到**的（stub 直接传引用，没有编解码）。
     * 若字段名写错、类型不兼容（如 long 变字符串）或 JSONB 转换失败，本用例会红。
     *
     * <p>用 PG 的 {@code ->>} 取文本值，避免依赖 JDBC 驱动的 JSONB→Java 类型映射差异
     * （不同驱动可能返回 {@code PGobject} 或 {@code String}，直接断言 Java 类型会脆）。
     */
    @Test
    void eventPayloadSurvivesJsonbRoundTrip() {
        seedApp();
        String roleCode = seedSystemRole(ROLE_PREFIX + "payload");
        String personId = seedPerson("S0400");
        grants.grant(new RoleGrantCmd(roleCode, "PERSON", personId, false, null, null, "MANUAL", OPERATOR));

        // 重算两次，使版本为 2：这样断言「事件里的版本 == 快照里的版本」才有区分度
        // （若恒为 1，即使事件体写错成常量 1 也会假绿）
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personId)));
        AuthzSnapshotView snapshot = recomputeAndFind(personId);
        assertThat(snapshot.version()).as("前置：版本应为 2").isEqualTo(2L);

        Map<String, Object> latest = jdbc.queryForMap("SELECT payload->>'type' AS type, "
                        + "payload->>'personId' AS person_id, payload->>'appCode' AS app_code, "
                        + "payload->>'snapshotVersion' AS snapshot_version, "
                        + "payload->>'changeType' AS change_type, payload->>'occurredAt' AS occurred_at "
                        + "FROM fdn_outbox WHERE aggregate = ? AND event_type = ? "
                        + "ORDER BY create_at DESC LIMIT 1",
                personId, AuthzChangedEvent.EVENT_TYPE);

        assertThat(latest.get("type")).isEqualTo(AuthzChangedEvent.EVENT_TYPE);
        assertThat(latest.get("person_id")).isEqualTo(personId);
        assertThat(latest.get("app_code")).isEqualTo(APP_CODE);
        assertThat(latest.get("snapshot_version"))
                .as("事件携带的版本必须与快照当前版本一致（否则客户端会按错误版本失效缓存）")
                .isEqualTo(String.valueOf(snapshot.version()));
        assertThat(latest.get("change_type")).isEqualTo(AuthzChangedEvent.CHANGE_TYPE_RECOMPUTED);
        assertThat(String.valueOf(latest.get("occurred_at")))
                .as("occurredAt 必须是可解析的时刻字符串")
                .isNotBlank()
                .isNotEqualTo("null");
        assertThat(java.time.Instant.parse(String.valueOf(latest.get("occurred_at"))))
                .as("occurredAt 应能按 ISO-8601 解析（客户端据此判断事件先后）")
                .isNotNull();
    }

    /**
     * ② 的负向口径：**撤销授权也必须产生事件**，否则客户端会一直用旧授权直到 TTL 到期。
     *
     * <p>这是「事件不静默丢失」的另一半——只测「授权后有事件」的话，
     * 「撤销后不发事件」这种更危险的缺陷（权限收回了但客户端仍放行）会被漏掉。
     * 走平台公开路径：撤销后显式重算（重算触发方式见类 javadoc）。
     */
    @Test
    void revokeThenRecomputeAlsoEmitsEventAndDropsPermission() {
        seedApp();
        String roleCode = seedSystemRole(ROLE_PREFIX + "revoke");
        String personId = seedPerson("S0500");
        String grantId = grants.grant(new RoleGrantCmd(roleCode, "PERSON", personId, false,
                null, null, "MANUAL", OPERATOR));

        AuthzSnapshotView granted = recomputeAndFind(personId);
        assertThat(granted.roles()).as("前置：授权后快照应含该角色").contains(roleCode);
        long versionAfterGrant = granted.version();
        int eventsAfterGrant = outboxEventsFor(personId).size();

        grants.revoke(grantId, OPERATOR);
        AuthzSnapshotView revoked = recomputeAndFind(personId);

        assertThat(revoked.roles())
                .as("撤销后重算，快照不得再含该角色")
                .doesNotContain(roleCode);
        assertThat(revoked.version())
                .as("撤销后的重算同样要递增版本（客户端靠版本变化感知失效）")
                .isGreaterThan(versionAfterGrant);
        assertThat(outboxEventsFor(personId))
                .as("撤销后的重算必须再发一条事件，否则客户端会一直用旧授权到 TTL 到期")
                .hasSize(eventsAfterGrant + 1);
    }

    // ---------------------------------------------------------------- 验收覆盖自证

    /**
     * 验收口径覆盖自证：F-00 §6.1 W84 点名的验收点必须**各有对应的 @Test 方法**。
     *
     * <p>为什么值得单独一条用例：本类另有辅助用例（清理不破坏种子等），若无这条清单，
     * 「闸门已建」很容易与「验收点已覆盖」被混为一谈——删掉或改名任一验收用例，
     * 闸门仍会以其余用例全绿而通过，缺哪条验收点无从发现。
     *
     * <p>方法名在此**刻意硬编码**：这是有意的脆弱性——改名任一验收用例必须同步更新本清单，
     * 从而强迫改动者显式确认「这条验收口径仍被覆盖」。
     *
     * <p>「starter 缓存失效」这半条口径不在本清单内，因为它按分层归属接入侧，
     * 由 {@code AuthorizationSnapshotInvalidationListenerTest} 覆盖（见类 javadoc）。
     */
    @Test
    void allServerSideAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "snapshotVersionIncrementsMonotonicallyPerPersonAndApp",   // 快照版本递增
                "snapshotVersionsAreIndependentAcrossPersonsAndApps",      // 版本按 (人员, 应用) 独立
                "recomputeWritesAuthzChangedEventToOutboxInSameTransaction", // 变更事件落库（同事务）
                "eventPayloadSurvivesJsonbRoundTrip",                      // 事件体经 JSONB 往返完好
                "revokeThenRecomputeAlsoEmitsEventAndDropsPermission");     // 撤销同样发事件（不静默丢）

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-00 §6.1 W84 的服务端验收点必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }

    // ---------------------------------------------------------------- 造数

    /**
     * 造本闸门专用的应用（而非复用种子的 {@code foundation}）。
     *
     * <p>原因与 {@code GfPermissionGateIT.seedApp} 同：种子应用行属于其它闸门共享的迁移数据，
     * 在其上写快照会污染同批次闸门，且清理时无法区分「本闸门写的」与「种子带的」。
     * 用专用应用 + 按 code 精确 DELETE，既不碰种子又可重复运行。
     *
     * <p>不需要幂等判断：{@code cleanUp()} 是 {@code @BeforeEach}，已按 code 删掉上一轮的本闸门应用，
     * 而 {@code fdn_app.code} 有唯一约束，重复 insert 会撞约束——真撞了就说明清理失效，应当暴露而非静默跳过。
     */
    private void seedApp() {
        apps.insert(App.register(APP_ID, APP_CODE, "闸门快照事件应用", null, null, "SUB", OPERATOR), OPERATOR);
    }

    /**
     * 造一个 {@code SYSTEM} 范围、不带任何权限码的角色。
     *
     * <p><b>为什么必须是 SYSTEM 范围</b>：{@code EffectiveRoleResolver.availableIn} 是
     * {@code appId.isEmpty() || role.systemScoped() || appId.equals(role.appId())}。本闸门要传
     * **真实 appCode**（否则 {@code AppIdResolver.resolve(null)} 返回 empty，快照就与具体应用无关，
     * 验不出「版本按 (人员, 应用) 独立」），而传了 appCode 之后只有全局角色才恒可用。
     *
     * <p><b>为什么不带权限码</b>：空权限码使 {@code HighRiskGrantReviewService.deferIfHighRisk}
     * 直接返回 false（授权真落库而非转待复核），且 {@code RoleMutexGuard} 无互斥对时直接放行。
     * 这样本闸门不必像 {@code GfPermissionGateIT} 那样关掉 high-risk-review。
     *
     * <p>{@code RoleApi} 没有 {@code find(code)}，且角色编码全局唯一；{@code cleanUp()} 已按
     * {@code gate.role.snapshot.%} 前缀删净，故直接 create。
     */
    private String seedSystemRole(String roleCode) {
        roles.create(new RoleCmd(roleCode, roleCode, "SYSTEM", null, null, List.of(), OPERATOR));
        return roleCode;
    }

    /**
     * 显式重算并读回快照。
     *
     * <p>{@code SnapshotApi} 只有 {@code recompute(cmd)}（返回重算条数）与 {@code find(appCode, personId)}
     * （命中即返回、未命中才按需重算），没有「重算并返回视图」的单一入口，故此处组合两步。
     * 顺序不可颠倒：先重算再读，才能保证读到的是本次重算的版本。
     */
    private AuthzSnapshotView recomputeAndFind(String personId) {
        snapshots.recompute(new SnapshotRecomputeCmd(APP_CODE, List.of(personId)));
        return snapshots.find(APP_CODE, personId)
                .orElseThrow(() -> new AssertionError("重算后必须能读到快照：" + personId));
    }

    /** 造人员。工号 {@code S0xxx} 前缀供 {@code cleanUp()} 精确清理。 */
    private String seedPerson(String empNo) {
        String id = Ids.newId(OrgIdPrefix.PERSON);
        // 手机 / 邮箱列均可空，本闸门不涉及敏感字段与查重
        persons.insert(new Person(id, empNo, "闸门人员-" + empNo, PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, null, null, "local", null, 0L));
        return id;
    }

    /**
     * 读本闸门产生的事件（按 {@code aggregate = personId} 精确过滤）。
     *
     * <p>刻意**不清理也不全表扫**{@code fdn_outbox}：它是跨域共享表（见类 javadoc「清理口径」），
     * 而 {@code aggregate} 用的是 {@code Ids.newId} 生成的 ULID，跨闸门与跨运行都不会撞，
     * 故按人过滤即可精确取到本闸门自己的事件。
     */
    private List<Map<String, Object>> outboxEventsFor(String personId) {
        return jdbc.queryForList("SELECT topic, event_type, aggregate, status, payload "
                        + "FROM fdn_outbox WHERE aggregate = ? AND event_type = ? ORDER BY create_at",
                personId, AuthzChangedEvent.EVENT_TYPE);
    }
}
