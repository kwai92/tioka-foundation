package com.foundation.server.gate;

import com.foundation.common.id.Ids;
import com.foundation.org.api.dto.PersonStatus;
import com.foundation.org.api.dto.PersonType;
import com.foundation.org.domain.OrgIdPrefix;
import com.foundation.org.domain.Person;
import com.foundation.org.infrastructure.PersonRepository;
import com.foundation.sync.api.dto.SyncMode;
import com.foundation.sync.api.dto.SyncPauseReason;
import com.foundation.sync.api.dto.SyncSourceStatus;
import com.foundation.sync.api.dto.SyncTaskStatus;
import com.foundation.sync.api.dto.SyncTriggerMode;
import com.foundation.sync.application.SyncEngine;
import com.foundation.sync.domain.SyncIdPrefix;
import com.foundation.sync.domain.SyncSource;
import com.foundation.sync.domain.SyncTask;
import com.foundation.sync.spi.ExternalIdentityHint;
import com.foundation.sync.spi.ExternalRecord;
import com.foundation.sync.spi.IncrementalCursor;
import com.foundation.sync.spi.IncrementalMode;
import com.foundation.sync.spi.Page;
import com.foundation.sync.spi.PageCursor;
import com.foundation.sync.spi.ProbeResult;
import com.foundation.sync.spi.SourceCapabilities;
import com.foundation.sync.spi.SyncCredentialsRef;
import com.foundation.sync.spi.SyncObjectType;
import com.foundation.sync.spi.SyncSourceAdapter;
import com.foundation.test.FoundationContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 闸门 GF（F-00 §6.1 W83，F-08 BAS-11 §6）：同步引擎的删除保护阈值暂停在**真库真事务**下成立。
 *
 * <p>验收口径（F-11 §6.1 / F-00 §6.1）：飞书部门与人员同步正确；<b>超阈值停用暂停待确认</b>；<b>删除保护生效</b>。
 *
 * <p><b>本闸门的覆盖范围（部分形态，如实标注）</b>：口径里的「飞书部门与人员同步正确」需要**真实飞书租户**
 * 拉真实组织/人员数据比对，而 gate 容器只有 PostgreSQL + Redis（无飞书/ LDAP 租户），端到端拉取不可达；
 * 且 {@code FeishuSyncAdapter} 的字段映射正确性已由 {@code FeishuFieldMappingTest} / {@code FieldMapperTest} /
 * {@code DiffPreviewTest} 等单测覆盖。故本闸门<b>不</b>验飞书数据正确性，只验<b>引擎编排层</b>——
 * 即「差异产生后，删除保护阈值判定 → 暂停 → 停用项被扣住不落地 → 发 {@code fdn.sync.paused} →
 * 确认后补落」这条链路在真库真事务下的最终状态。这恰是 {@code DeleteProtectionPolicyTest}（纯函数单测）
 * 与 {@code DiffPreviewTest}（差异算法单测）<b>碰不到</b>的部分。
 *
 * <p><b>与既有单测的分工（不重复覆盖算法）</b>：
 * <ul>
 *   <li>{@code DeleteProtectionPolicyTest} 已覆盖 {@code evaluate} 的<b>阈值算法</b>（5%/50 人/10 组织、
 *       严格大于、边界不误判）——本闸门<b>不</b>重测算法；</li>
 *   <li>{@code DiffPreviewTest} 已覆盖<b>差异如何产生</b>（新增/修改/停用/移动）——本闸门<b>不</b>重测 diff；</li>
 *   <li>本闸门验的是 {@code SyncEngine.land} 把上述两者<b>编排</b>起来后，在真库里的<b>可观测结果</b>：
 *       {@code fdn_sync_task.status=PAUSED}、{@code pause_reason=THRESHOLD}、被暂停的停用项<b>没有</b>真的写进
 *       {@code fdn_person}（删除保护生效）、{@code fdn.sync.paused} 落 {@code fdn_outbox}；
 *       以及 {@code confirm} 后停用项<b>真的</b>落地（反证暂停不是空操作）。</li>
 * </ul>
 *
 * <p><b>如何在 gate 里驱动引擎而不接真实租户</b>：{@code AdapterRegistry} 从 Spring 容器<b>发现全部</b>
 * {@link SyncSourceAdapter} bean 建索引（{@code foundation.sync.adapters} 为空时装载全部）。本类用一个
 * {@link TestConfiguration} 注册 {@code sourceType="GATE_FAKE"} 的假适配器，并把
 * {@code foundation.sync.adapters} 限定为 {@code GATE_FAKE}——这样既保证假适配器被装载，又屏蔽 feishu/ldap
 * 真实适配器（它们需要外部租户）。假适配器的 {@code fetchFull} 返回<b>空页</b>：全量同步时
 * 「本地已有、来源快照缺失」的人员会被 {@code DiffPreviewService} 判为 DISABLE（见其 §68-72），
 * 于是本地 3 个在职人员 → 3 条停用差异 → {@code 3*100 > 3*5} 触发百分比阈值。造数极简且完全可控。
 *
 * <p><b>关于「1 分钟 / 5 秒」等时限</b>：F-11 §7 的时限是生产观察项，闸门内不做计时断言
 * （理由同 {@code GfSnapshotEventGateIT}：闸门跑在毫秒级、测不出真实时限，硬断言只会得到脆弱用例）。
 *
 * <p><b>清理口径（gate 共享 static PG）</b>：{@code fdn_person} 被多个闸门共享，但本闸门造的人员
 * {@code source='GATE_FAKE'}（其它闸门用 {@code 'local'}），故按 {@code source} 精确删、<b>绝不 TRUNCATE</b>；
 * {@code fdn_sync_source.code} 带 {@code gfsync} 前缀。删除顺序遵守外键：
 * {@code fdn_sync_log}/{@code fdn_sync_conflict}（REFERENCES task）→ {@code fdn_sync_task}
 * （REFERENCES source）→ {@code fdn_sync_field_mapping} → {@code fdn_sync_source} → {@code fdn_person}；
 * {@code fdn_outbox} 跨域共享<b>不整表清</b>，只按本闸门产生的 taskId / personId 作 aggregate 精确删。
 *
 * <p>运行：{@code mvn -B verify -Pgate}（gate profile 由 surefire 以 {@code groups=gate} 执行 IT）。
 */
@Tag("gate")
@SpringBootTest(properties = {
        // 只装载本闸门的假适配器，屏蔽需要真实租户的 feishu/ldap 适配器
        "foundation.sync.adapters=GATE_FAKE"
})
class GfSyncGateIT {

    /** 假适配器的来源类型，也是本闸门所造 person 的 {@code source} 值（天然与其它闸门隔离）。 */
    private static final String SOURCE_TYPE = "GATE_FAKE";
    /** 本闸门来源编码前缀，清理据此精确 DELETE。 */
    private static final String CODE_PREFIX = "gfsync";
    private static final String OPERATOR = "gate";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        FoundationContainers.register(registry);
    }

    /** 注册假适配器：AdapterRegistry 从容器发现 SyncSourceAdapter，故此处声明即被装载。 */
    @TestConfiguration
    static class FakeAdapterConfig {
        @Bean
        SyncSourceAdapter gateFakeAdapter() {
            return new GateFakeAdapter();
        }
    }

    /**
     * 假同步适配器：全量拉取返回<b>空页</b>，使本地已有人员全部判为「来源已删除」→ DISABLE 差异。
     * 其余方法返回空/原样，因为阈值暂停场景只走 FULL 全量、不碰增量与外部身份。
     */
    static class GateFakeAdapter implements SyncSourceAdapter {
        @Override
        public String sourceType() {
            return SOURCE_TYPE;
        }

        @Override
        public SourceCapabilities capabilities() {
            return new SourceCapabilities(Set.of(SyncObjectType.PERSON), Set.of(IncrementalMode.FULL_ONLY),
                    false, false);
        }

        @Override
        public void authenticate(SyncCredentialsRef credentials) {
            // no-op：假适配器不连外部
        }

        @Override
        public ProbeResult probe() {
            return ProbeResult.ok(List.of());
        }

        @Override
        public Page<ExternalRecord> fetchFull(SyncObjectType objectType, PageCursor cursor) {
            return Page.empty();
        }

        @Override
        public Page<ExternalRecord> fetchIncremental(SyncObjectType objectType, IncrementalCursor cursor) {
            return Page.empty();
        }

        @Override
        public Optional<ExternalRecord> fetchOne(SyncObjectType objectType, String externalId) {
            return Optional.empty();
        }

        @Override
        public IncrementalCursor advanceCursor(IncrementalCursor cursor, Page<ExternalRecord> page) {
            return cursor;
        }

        @Override
        public Optional<ExternalIdentityHint> resolveExternalIdentity(ExternalRecord record) {
            return Optional.empty();
        }
    }

    @Autowired
    private SyncEngine engine;

    @Autowired
    private PersonRepository persons;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        String sourceIdSub = "(SELECT id FROM fdn_sync_source WHERE code LIKE '" + CODE_PREFIX + "%')";
        String taskIdSub = "(SELECT id FROM fdn_sync_task WHERE source_id IN " + sourceIdSub + ")";
        // fdn_outbox 跨域共享，只按本闸门产生的 aggregate 精确删（taskId 与 personId 两类）
        jdbc.update("DELETE FROM fdn_outbox WHERE aggregate IN " + taskIdSub);
        jdbc.update("DELETE FROM fdn_outbox WHERE aggregate IN "
                + "(SELECT id FROM fdn_person WHERE source = ?)", SOURCE_TYPE);
        // 外键顺序：log/conflict → task → field_mapping → source → person
        jdbc.update("DELETE FROM fdn_sync_log WHERE task_id IN " + taskIdSub);
        jdbc.update("DELETE FROM fdn_sync_conflict WHERE task_id IN " + taskIdSub);
        jdbc.update("DELETE FROM fdn_sync_task WHERE source_id IN " + sourceIdSub);
        jdbc.update("DELETE FROM fdn_sync_field_mapping WHERE source_id IN " + sourceIdSub);
        jdbc.update("DELETE FROM fdn_sync_source WHERE code LIKE ?", CODE_PREFIX + "%");
        // fdn_person 共享表，只删本闸门 source（其它闸门用 'local'），绝不 TRUNCATE
        jdbc.update("DELETE FROM fdn_person WHERE source = ?", SOURCE_TYPE);
    }

    // ---------------------------------------------------------------- ① 超阈值暂停 + 删除保护

    /**
     * ① 一次停用超过阈值 → 任务暂停待确认，且停用项<b>不落地</b>（F-08 §6「超阈值停用暂停待确认」
     * + 「删除保护生效」）。
     *
     * <p>造 3 个在职人员、假适配器全量返回空 → 3 条 DISABLE 差异 → {@code 3*100 > 3*5} 触发百分比阈值。
     * 断言三处一致，缺一都可能掩盖缺陷：
     * <ul>
     *   <li><b>返回值</b>：{@code outcome.pause()==true}；</li>
     *   <li><b>任务库</b>：{@code fdn_sync_task.status=PAUSED} 且 {@code pause_reason=THRESHOLD}；</li>
     *   <li><b>删除保护（核心）</b>：3 个人员在 {@code fdn_person} 里<b>仍是 ACTIVE</b>——
     *       {@code land} 对 {@code verdict.pause()} 的 DISABLE 项 {@code continue} 跳过，
     *       只断言任务状态会漏掉「状态标了 PAUSED 却仍把人停用了」这类删除保护失效缺陷；</li>
     *   <li><b>事件</b>：{@code fdn.sync.paused} 以 taskId 为 aggregate 落 {@code fdn_outbox}
     *       （接入方/运维据此感知需人工确认）。</li>
     * </ul>
     */
    @Test
    void runPausesAndHoldsDisablesWhenOverThreshold() {
        SyncSource source = seedSource("a");
        seedActivePerson(1);
        seedActivePerson(2);
        seedActivePerson(3);

        SyncEngine.SyncOutcome outcome = engine.run(source, SyncMode.FULL, SyncTriggerMode.MANUAL, OPERATOR);

        assertThat(outcome.pause())
                .as("3 个在职人员全部被判停用，超过 5% 阈值，应触发暂停")
                .isTrue();
        assertThat(outcome.task().status())
                .as("任务状态应置 PAUSED")
                .isEqualTo(SyncTaskStatus.PAUSED);
        assertThat(outcome.task().pauseReason())
                .as("暂停原因应是删除保护阈值")
                .isEqualTo(SyncPauseReason.THRESHOLD);

        // 删除保护：被暂停的停用项不得落地，人员在库里仍是 ACTIVE
        assertThat(activePersonCount())
                .as("删除保护生效：暂停时停用项被扣住，3 个人员应仍全部 ACTIVE（未被停用）")
                .isEqualTo(3);
        assertThat(disabledPersonCount())
                .as("暂停期间不应有任何人员被停用")
                .isEqualTo(0);

        // 事件：以 taskId 为 aggregate 落 outbox
        List<String> events = jdbc.queryForList(
                "SELECT event_type FROM fdn_outbox WHERE aggregate = ?", String.class, outcome.task().id());
        assertThat(events)
                .as("暂停必须发 fdn.sync.paused（运维/接入方据此感知需人工确认）")
                .contains("fdn.sync.paused");

        // 任务库状态与返回值一致（withStatus 确实写库，而非只在内存对象上）
        assertThat(jdbc.queryForObject("SELECT status FROM fdn_sync_task WHERE id = ?",
                String.class, outcome.task().id()))
                .as("PAUSED 状态必须落库，否则重跑/审计看不到暂停")
                .isEqualTo("PAUSED");
    }

    // ---------------------------------------------------------------- ② 确认后补落被暂停的停用

    /**
     * ② 暂停后 {@code confirm} → 跳过阈值、补落之前被扣住的停用（F-08 §6「暂停待确认」的确认分支）。
     *
     * <p>这是 ① 的<b>反向证明</b>：若 ① 的「人员仍 ACTIVE」是因为引擎根本没产生停用差异（空操作假绿），
     * 那么 confirm 后人员不会变 DISABLED。本用例先 run 到暂停、再 confirm，断言 3 个人员<b>真的</b>被停用、
     * 且新任务 SUCCEEDED（不再暂停）——坐实 ① 的暂停确实「扣住」了本应执行的停用，而非什么都没做。
     *
     * <p>{@code confirm} 内部 {@code thresholdConfirmed=true} → {@code verdict=pass()} → 停用项照常落地；
     * 因落地幂等，重跑不会重复新增/修改，只补齐被暂停的停用（见 {@code SyncEngine.confirm} javadoc）。
     */
    @Test
    void confirmAfterPauseAppliesTheDeferredDisables() {
        SyncSource source = seedSource("b");
        seedActivePerson(1);
        seedActivePerson(2);
        seedActivePerson(3);

        SyncEngine.SyncOutcome paused = engine.run(source, SyncMode.FULL, SyncTriggerMode.MANUAL, OPERATOR);
        assertThat(paused.pause()).as("前置：首轮应暂停").isTrue();
        assertThat(activePersonCount()).as("前置：暂停时人员仍 ACTIVE").isEqualTo(3);

        SyncEngine.SyncOutcome confirmed = engine.confirm(source, paused.task(), OPERATOR);

        assertThat(confirmed.pause())
                .as("confirm 跳过阈值判定，不应再暂停")
                .isFalse();
        assertThat(confirmed.task().status())
                .as("确认后任务应成功完成")
                .isEqualTo(SyncTaskStatus.SUCCEEDED);
        assertThat(disabledPersonCount())
                .as("确认后被扣住的停用应全部补落：3 个人员变为 DISABLED（反证 ① 的暂停不是空操作）")
                .isEqualTo(3);
        assertThat(activePersonCount())
                .as("确认后不再有 ACTIVE 人员")
                .isEqualTo(0);
    }

    // ---------------------------------------------------------------- ③ 未超阈值正常完成

    /**
     * ③ 无差异（未超阈值）→ 任务成功、发 {@code fdn.sync.completed}（正常路径基线）。
     *
     * <p>没有在职人员、假适配器又返回空 → 差异为空 → 阈值判定 pass → 任务 SUCCEEDED。
     * 这条为 ①② 提供<b>对照基线</b>：证明 SUCCEEDED/completed 是常态，PAUSED/paused 只在超阈值时出现，
     * 而非引擎恒暂停。缺这条，①的「PAUSED」可能与「引擎对任何输入都暂停」混淆。
     */
    @Test
    void runSucceedsAndEmitsCompletedWhenNoDiff() {
        SyncSource source = seedSource("c");
        // 刻意不造人员：来源空、本地空 → 无差异

        SyncEngine.SyncOutcome outcome = engine.run(source, SyncMode.FULL, SyncTriggerMode.MANUAL, OPERATOR);

        assertThat(outcome.pause())
                .as("无差异不应暂停")
                .isFalse();
        assertThat(outcome.task().status())
                .as("无差异任务应成功")
                .isEqualTo(SyncTaskStatus.SUCCEEDED);
        List<String> events = jdbc.queryForList(
                "SELECT event_type FROM fdn_outbox WHERE aggregate = ?", String.class, outcome.task().id());
        assertThat(events)
                .as("正常完成应发 fdn.sync.completed")
                .contains("fdn.sync.completed");
    }

    // ---------------------------------------------------------------- 覆盖自证

    /**
     * 验收口径覆盖自证：F-08 §6 平台侧可验的三条必须<b>各有对应的 @Test 方法</b>。
     *
     * <p>方法名<b>刻意硬编码</b>：删改任一验收用例即红，防「闸门已建」与「验收点已覆盖」被混为一谈
     * （沿用 {@code GfPermissionGateIT} / {@code GfOffboardingGateIT} 同款惯例）。
     * 清单<b>不含</b>「飞书部门与人员同步正确」——它需真实飞书租户，在本闸门边界外（见类 javadoc），
     * 列入会给出虚假的「已覆盖」信号。
     */
    @Test
    void allPlatformSideAcceptancePointsAreCovered() {
        Set<String> acceptancePoints = Set.of(
                "runPausesAndHoldsDisablesWhenOverThreshold",   // ① 超阈值暂停 + 删除保护生效
                "confirmAfterPauseAppliesTheDeferredDisables",  // ② 确认后补落（反证暂停非空操作）
                "runSucceedsAndEmitsCompletedWhenNoDiff");       // ③ 未超阈值正常完成（对照基线）

        Set<String> declaredTests = Arrays.stream(getClass().getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Test.class))
                .map(Method::getName)
                .collect(Collectors.toSet());

        assertThat(declaredTests)
                .as("F-08 §6 平台侧可验的验收点必须各有对应 @Test，缺一条即视为闸门不完整")
                .containsAll(acceptancePoints);
    }

    // ---------------------------------------------------------------- 造数与工具

    /** 造一个 GATE_FAKE 类型的同步来源（authority=false，避开「单一权威来源」部分唯一索引）。 */
    private SyncSource seedSource(String suffix) {
        String id = Ids.newId(SyncIdPrefix.SOURCE);
        String code = CODE_PREFIX + "-" + suffix + "-" + shortUlid();
        jdbc.update("INSERT INTO fdn_sync_source (id, code, name, source_type, authority, adapter_impl, "
                        + "capabilities_json, config_json, status) VALUES (?, ?, ?, ?, false, 'gate.fake', '{}', '{}', 'ENABLED')",
                id, code, "闸门同步源-" + suffix, SOURCE_TYPE);
        SourceCapabilities caps = new SourceCapabilities(Set.of(SyncObjectType.PERSON),
                Set.of(IncrementalMode.FULL_ONLY), false, false);
        return new SyncSource(id, code, "闸门同步源-" + suffix, SOURCE_TYPE, false, null, "gate.fake", null,
                caps, Map.of(), SyncSourceStatus.ENABLED, null, Instant.now());
    }

    /** 造一个在职（ACTIVE）人员，source=GATE_FAKE、externalId 唯一，使其被引擎计入选中来源的现存人员。 */
    private void seedActivePerson(int seq) {
        String id = Ids.newId(OrgIdPrefix.PERSON);
        String empNo = "SY" + seq + shortUlid();
        String externalId = "gfsync-ext-" + seq + "-" + shortUlid();
        persons.insert(new Person(id, empNo, "闸门同步人员-" + seq, PersonType.EMPLOYEE, PersonStatus.ACTIVE,
                null, null, null, null, SOURCE_TYPE, externalId, 0L));
    }

    private int activePersonCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM fdn_person WHERE source = ? AND status = 'ACTIVE'",
                Integer.class, SOURCE_TYPE);
        return n == null ? 0 : n;
    }

    private int disabledPersonCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM fdn_person WHERE source = ? AND status = 'DISABLED'",
                Integer.class, SOURCE_TYPE);
        return n == null ? 0 : n;
    }

    /** 取完整 ULID 随机位（不取毫秒时间戳前缀，避免同毫秒内造数重复）。 */
    private static String shortUlid() {
        return Ids.newId(OrgIdPrefix.PERSON).substring(4).toLowerCase();
    }
}
