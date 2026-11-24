# 运行时降级策略（ADP-08，第一批）

> 默认 **fail-closed**：Foundation 不可用且无有效缓存时明确失败，不返回陈旧数据、更不静默返回 `null`。本文是「中台临时故障」时的降级策略；第四批落地的 `foundation.client.mode=local|self-managed` 是「根本不接中台」的运行模式，两者是独立路径，见 [接入运行模式](runtime-modes.md)。

## 1. 三条规则

1. **只读主数据**（字典 / 参数 / 组织 / 人员）：可按能力配置为「过期缓存兜底 + 降级标记」，
   决策出口是 `Degrade.policyFor(capability)`，白名单外一律 `FAIL_CLOSED`。
2. **身份与授权**：**当前默认且实际行为都是 deny**，不存在生产可用的过期放行。
   授权快照有两条实现路径，降级决策出口正在收敛（见 §2.1）：
   - **轻量路径**（`foundation-starter-client`，Tioka 与第二接入方实际走的那条）已收敛为
     只消费 `Degrade.policyFor("authz")`。因 `authz` 不在白名单，恒为 `FAIL_CLOSED`，
     过期且回源失败一律拒绝；接入方**无法**通过任何配置放宽。
   - **标准路径**（`foundation-starter-security` 的 `SnapshotCache`）仍自行解析
     `foundation.authz.snapshot.fail-mode`，保留了 `stale` 分支。这是**尚未收敛的第二个
     决策出口**，其行为归属待决策 D4；**生产环境在 D4 拍板前不应启用 `stale`**。
3. **写接口**（编号取号、文件上传、删除等）：**不降级、不兜底**，直接失败，避免重复与非幂等副作用。

## 2. 配置项

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `foundation.client.degrade.policy` | `fail-closed` | 全局策略；`stale-cache` 才允许使用过期缓存 |
| `foundation.client.degrade.max-stale` | `30m` | 过期缓存可容忍的最大陈旧时长，超过即拒绝兜底 |
| `foundation.client.degrade.capabilities` | `dict,param,org,person` | 允许降级的能力白名单；**白名单外一律 fail-closed** |
| `foundation.authz.snapshot.fail-mode` | `deny` | 授权快照策略；`stale` 为过期放行。**仅标准路径生效**，待决策 D4 管辖 |
| `foundation.authz.snapshot.stale-max-age` | `10m` | 授权快照陈旧上限（同上，仅标准路径） |
| `foundation.authz.snapshot.cache-ttl` | `5m` | **标准路径**（`foundation-starter-security`）的 `SnapshotCache` TTL |
| `foundation.client.authz.cache-ttl` | `5m` | **轻量路径**（`foundation-starter-client`）P1 版本化缓存 TTL；`0s` 退回每次回源。刻意不与上面的 security starter 配置共用，避免两套缓存语义串线 |
| `foundation.client.mode` | `foundation` | 接入运行模式；`local` / `self-managed` 下不装配中台客户端，详见 [接入运行模式](runtime-modes.md) |
| `foundation.client.local.enabled` | `false` | 独立模式下是否启用配置种子实现（dict/param/org/person） |

### 2.1 降级决策出口的现状（本批收敛）

`Degrade.policyFor(capability)` 是**只读主数据**的唯一判定出口：即使全局打开 `stale-cache`，
`security` / `authz` 等不在白名单的能力仍返回 `FAIL_CLOSED`。配置拼写错误（如 `fail-open`）
会**启动期快速失败**，不会被当成 deny 悄悄放过。

授权快照目前有**两个**决策出口，这是已知欠账而非设计目标：

| 出口 | 位置 | 行为 | 谁在消费 |
| --- | --- | --- | --- |
| `Degrade.policyFor("authz")` | starter-client | 恒 `FAIL_CLOSED`，接入方不可放宽 | Tioka、`foundation-it-example`（轻量路径，活跃） |
| `FoundationAuthzProperties.isStaleAllowed()` | starter-security | 可配 `stale` 过期放行 | 标准路径，当前无活跃接入方 |

收敛方向：让标准路径也只消费 `policyFor("authz")`，把 `fail-mode` 退化为 `policyFor` 的
输入或移除。**本批刻意不动标准路径行为**——它归属待决策 D4（授权是否允许 fail-open），
代其拍板属于越权决策。D4 若拍板「一律禁止」，只需删掉 `isStaleAllowed()` 的 `stale` 分支；
若拍板「允许」，只需把 `authz` 纳入降级白名单，轻量路径无需改动即可生效。


## 3. 降级链路

```
HTTP 请求 → DegradeHeaderFilter（最外层）
          → 业务代码 → DictClient / ParamClient / OrgClient / PersonClient
          → TtlCache.getWithDegrade(key, loader)
              ├─ 命中未过期缓存：直接返回
              ├─ 回源成功：刷新缓存返回
              ├─ 回源返回 null：业务空结果，按原语义返回（不算降级）
              └─ 回源抛异常：
                   ├─ FAIL_CLOSED / 无缓存 / 超陈旧上限 → FoundationUnavailableException
                   └─ STALE_CACHE → 返回过期值 + WARN 审计日志 + DegradeContext.mark()
          ← 响应头 X-Foundation-Degraded: true（未提交时）+ 清理 ThreadLocal
```

- `FoundationUnavailableException` 携带资源标识与原始原因，替代过去「熔断中抛 `IllegalStateException`」「客户端静默返回 `null`」的不可区分行为，便于网关映射 503 与业务方决策。
- WARN 审计日志格式：`AUDIT foundation.read.degrade 使用过期缓存：key=... staleMs=...`；授权快照用 `AUDIT foundation.authz.snapshot.degrade`，含 `appCode / personId / staleMs / reason`。
- `DegradeHeaderFilter` 在请求结束时读标记并写响应头，随后清理 `ThreadLocal`，避免线程池复用串标记。**若响应体已提交（流式下载等），响应头无法再写**，此时以 WARN 日志与 SDK 侧标记为准。

### 3.1 异步线程传递（P1）

纯 `ThreadLocal` 在 `@Async` / 线程池 / 消息回调里会丢标记：子线程打了降级，外层过滤器读不到，响应头不写。P1 起 `DegradeContext` 提供显式传播：

```java
DegradeContext.Snapshot snapshot = DegradeContext.capture();   // 过滤器已在请求入口绑定
executor.submit(DegradeContext.wrap(snapshot, () -> doWork())); // 或 DegradeContext.call(snapshot, supplier)
```

`wrap` / `call` 进入时 `restore(snapshot)`、退出时必定 `clear()`；子线程里的 `DegradeContext.mark()` 会回写到父子共享的 `Snapshot`，父线程 `isMarked()` 仍能读到。不调用 `capture()` 的存量代码行为完全不变，`clear()` 依旧按线程清理。

### 3.2 轻量路径的授权快照缓存（P1）

`foundation-starter-client` 的 `AuthorizationSnapshotCache` 与标准路径 `SnapshotCache` 同构但独立：命中新鲜快照即零回源，`fdn.authz.changed` 经 `AuthorizationSnapshotInvalidationListener` 失效，TTL 只是事件丢失兜底。

该缓存**不自带降级决策**，只消费构造时注入的 `DegradePolicy`，装配层传入
`Degrade.policyFor("authz")`。因 `authz` 不在降级白名单，实际恒为 `FAIL_CLOSED`：
条目过期且回源失败一律返回 `null` 并删除条目。接入方没有任何配置可以放宽它。

代码中保留的 `STALE_CACHE` 分支是为 D4 拍板后准备的**唯一改点**（把 `authz` 加进白名单即可，
两条路径同时生效），当前不可达，由 `AuthorizationSnapshotCacheTest` 的「默认策略为 FAIL_CLOSED
时过期即拒绝」锁定。两条与降级策略**解耦**的硬拒绝，任何策略下都不可放宽：

- **版本回退**：回源版本小于已缓存版本 → 拒绝。数据错误不等于「中台不可用」，
  否则权限会静默回退到旧版本。
- **身份串号**：回源结果的 `personId` / `appCode` 与请求不一致 → 拒绝且不入缓存。

## 4. 授权快照的等保边界

标准路径 `SnapshotCache` 原先把「缓存过期且回源失败」写死为 `FORBIDDEN`（F-07 §4 / S15 §11.3）。ADP-08 把 `FoundationAuthzProperties.Snapshot.failMode` 接线：

- `deny`（默认）：行为与改造前完全一致，由 `SnapshotDegradeTest` 覆盖。
- `stale`：仅在存在过期快照且未超 `stale-max-age` 时放行；同时写审计 WARN 日志并打降级标记；`load()` 与 `version()` 走同一出口。
- 确定性业务拒绝（`BizException`）不降级，避免把 403 掩盖成过期放行。

> **待决策 D4**：36 号规划第 9 节 D4 的**建议**是「身份与授权类不允许 fail-open」，
> 但该条仍在「待负责人决策」表中，**尚未拍板**。因此本批只收敛了轻量路径的决策出口，
> 未改动标准路径的 `stale` 分支——代其拍板属越权决策。
> D4 若拍板「禁止」：删掉 `isStaleAllowed()` 的 `stale` 分支，轻量路径无需改动。
> D4 若拍板「允许」：把 `authz` 纳入 `foundation.client.degrade.capabilities`，轻量路径自动生效，
> 再决定是否收敛标准路径到同一出口。
> **无论 D4 如何，生产环境在拍板前不应启用 `stale`。**


## 5. 测试覆盖

| 测试 | 断言 |
| --- | --- |
| `TtlCacheDegradeTest` | 默认拒绝并抛明确异常、过期兜底返回旧值并打标记、超陈旧上限拒绝、无缓存拒绝、未过期不回源、`null` 空结果不误判为降级 |
| `DegradePolicyConfigTest` | 白名单内可兜底、`security/authz` 强制拒绝、收窄白名单生效 |
| `StaleSnapshotPolicyTest` | `stale` 在上限内放行并打标记、`version()` 同策略、超上限/无缓存仍拒绝、非法 failMode 快速失败 |
| `DegradeHeaderFilterTest` | 响应头透传、未降级不写头、连续请求不串标记 |
| `SnapshotDegradeTest`（既有） | 默认 deny 行为未被本批改变 |
| `RuntimeModeAssemblyTest`（第四批） | 三种模式装配互斥、独立模式不建连中台、总开关优先 |
| `LocalFoundationDataTest`（第四批） | 本地种子实现的行为契约 |
| `DegradeContextTest`（P1） | 父子线程传递、子线程回写父请求、异常仍清理、请求间隔离、`restore(null)` |
| `AuthorizationSnapshotCacheTest`（P1） | 命中零回源、TTL 到期回源、陈旧拒绝、版本回退拒绝、身份串号拒绝、事件失效、`cache-ttl=0s`；**注入 FAIL_CLOSED 时过期即拒绝且不打标记**、**注入 STALE_CACHE 时上限内放行并打标记**、超上限拒绝、版本回退与身份串号在任何策略下都硬拒绝 |
| `SecondAppOnboardingTest`（P1） | 第二接入方零代码获得缓存与事件失效、陈旧快照绝不放行 |
| `CedarNativeSupport` + `CedarEvaluatorTest` / `CharterCompilerTest`（Tioka 侧） | 缺 cedar-java 原生库时按 Assumptions 跳过并计入 skipped，编译侧断言照常执行，`mvn test` 退出码恢复可判定 |

## 6. 仍未完成

- **标准路径降级出口未收敛**（§2.1）：`foundation-starter-security` 的 `SnapshotCache` 仍自行
  解析 `foundation.authz.snapshot.fail-mode`，与 `Degrade.policyFor("authz")` 并存。
  受**待决策 D4** 阻塞，本批刻意不动其行为，避免代负责人拍板授权是否允许 fail-open。
- **自管授权导入器**（ADP-08 余下部分）：三模式与配置种子本地实现已于第四批落地（[接入运行模式](runtime-modes.md)），`SELF_MANAGED` 目前只有模式与装配位置；`SelfManagedImporter` 属 F2 范围，未实现。
- Tioka 侧 `tioka.foundation.mode` 与 starter 侧 `foundation.client.mode` 的一致性联动（F1 接线时补启动期校验）。
- 若干 `Http*Client`（日历 `workday` / `addWorkdays` / `diffWorkdays`、编号 `current`）在服务端返回空响应时仍返回 `null`；本批未改动，因为它们属于**业务空结果**而非「服务不可用」，需要在契约层明确定义后才能统一改成 `Optional` 或异常。
- 降级事件写入审计中心（目前仅结构化 WARN 日志）、指标化（Micrometer counter）、告警通道 SDK 接入、以及 `GaDegradeGateIT`（Foundation 停机 10 分钟的端到端验证）。**注意**：用既有 WARN 日志即可落地的采集与告警规则见 §7，本条剩余的是「指标化 + 写审计中心」这部分代码级增强。
- 缓存降级目前只覆盖进程内 `TtlCache`；引入 `foundation-starter-cache` 的 Redis 实现后需提供同构的过期兜底语义（ADP-06）。

## 7. 授权快照降级的采集与告警（部署要求）

§3 的 WARN 审计日志是**代码已经发出的**结构化输出；本节把它们落成运维侧可执行的采集与告警规则。
这不改任何代码、不触碰待决策 D4——D4 管辖的是「标准路径是否允许 `stale` 放行」，而本节要求的是
「无论走哪条路径、无论 D4 如何，**授权被拒绝或被降级放行都必须可观测、可告警**」。

### 7.1 两个日志前缀的语义与触发条件

| 前缀 | 级别 | 触发条件 | 运维含义 |
| --- | --- | --- | --- |
| `AUDIT foundation.authz.snapshot.deny` | WARN | 授权快照**被拒绝放行**：回源异常 / 回源 null / 身份串号 / 版本回退 / `stale` 超陈旧上限 | 有人的授权判定失败了。**偶发**通常等于中台抖动；**突增**是告警项 |
| `AUDIT foundation.authz.snapshot.degrade` | WARN | 过期快照在 `stale-max-age` 内**被降级放行**（打了 `DegradeContext` 标记） | 中台不可用期间放行了陈旧授权。**默认配置下两条路径都不可达**，一旦出现即说明有人启用了 `stale` |

> **`degrade` 前缀出现 = 配置已偏离默认**。轻量路径（starter-client）恒 `FAIL_CLOSED`、
> 标准路径（starter-security）默认 `deny`，二者在默认配置下都**不会**发出 `degrade`。
> 故该前缀一旦出现，意味着部署侧显式打开了 `foundation.authz.snapshot.fail-mode=stale`
> ——而 §4 与待决策 D4 明确要求「**生产环境在 D4 拍板前不应启用 `stale`**」。
> 这条日志前缀本身就是该技术约束的**运行时兜底探针**：任何一次 `degrade` 都应触发工单核查配置来源。

### 7.2 两条路径的可观测性差异（务必据实配置，勿假设对称）

**这是最容易配错的地方**：两条路径在**默认 deny** 下的可观测载体不同。

- **轻量路径**（`foundation-starter-client`，Tioka 与 `foundation-it-example` 实际走的活跃路径）：
  默认 deny 时**一定发** `AUDIT ...deny` 日志（`AuthorizationSnapshotCache` 四种拒绝分支 +
  `CachingAuthorizationSnapshotClient` 的读取异常兜底），随后返回 `null` 交上层判定器拒绝。
  → **仅靠日志前缀即可完整采集轻量路径的全部授权拒绝。**
- **标准路径**（`foundation-starter-security` 的 `SnapshotCache`）：默认 `deny` 时
  **不发 `AUDIT ...deny` 日志**，而是抛 `BizException(ErrorCode.FORBIDDEN)`（→ HTTP 403），
  message 以「授权快照不可用：」开头。只有 `fail-mode=stale` **且超出** `stale-max-age` 时，
  才在抛异常前补一条 `AUDIT ...deny`。
  → **标准路径的默认拒绝必须额外靠 HTTP 403 计数或异常日志采集**，
  只盯 `AUDIT foundation.authz.snapshot` 前缀会漏掉它绝大多数拒绝。

因此标准路径的告警需要**两个信号源并用**：
1. `AUDIT foundation.authz.snapshot.deny` / `.degrade`（覆盖 `stale` 模式下的边界）；
2. 业务 403 计数中 message 匹配「授权快照不可用：」的子集（覆盖默认 `deny` 模式）。
若只部署轻量路径（当前 Tioka 的实际形态），信号源 1 已足够。

### 7.3 建议的采集与告警规则（可直接落地，不依赖未完成的代码增强）

以日志采集器（Loki / ELK / CloudWatch Logs 等任一）按前缀抽取字段即可，**无需等 §6 的指标化改造**：

- **抽取字段**：两条日志均含 `appCode` / `personId`；`deny` 另有 `cause`，`degrade` 另有 `staleMs` / `reason`。
  建议按 `appCode` 聚合（定位是哪个接入应用），按 `personId` 去重（避免单人重试刷量）。
- **告警 G1（可用性）**：单位时间窗内 `AUDIT foundation.authz.snapshot.deny` 计数 > 阈值
  → 告警。阈值按基线设定：偶发（个位数/分钟）通常是中台抖动，**突增**（较基线数倍）才是事件。
- **告警 G2（配置漂移，最高优先级）**：**任意一条** `AUDIT foundation.authz.snapshot.degrade`
  → 立即告警 + 工单。理由见 §7.1：默认配置不可达，出现即配置偏离、且违反「D4 拍板前不启用 `stale`」。
- **告警 G3（标准路径专用）**：HTTP 403 中 message 含「授权快照不可用：」的计数突增 → 告警。
  仅在部署了 `foundation-starter-security` 标准路径时需要（见 §7.2）。
- **响应头旁路**：降级放行会经 `DegradeHeaderFilter` 写 `X-Foundation-Degraded: true`（§3）。
  网关侧统计该响应头占比，可作为 `degrade` 事件的**独立交叉验证**信号；
  但注意 §3 已说明「响应体已提交（流式下载）时写不了头」，故它只是补充、不能替代日志采集。

### 7.4 与待决策 D4 的边界（本节不代 D4 拍板）

本节只规定「如何观测与告警」，**不改变任何降级决策行为**：

- D4 若拍板「禁止授权 fail-open」：删掉 `isStaleAllowed()` 的 `stale` 分支后，`degrade` 前缀将彻底不可达，
  告警 G2 退化为「理论上永不触发、一旦触发即为严重回归」的看门狗——**规则无需修改**。
- D4 若拍板「允许授权降级」：`degrade` 变为正常业务信号，届时把告警 G2 从「立即工单」调整为
  「按 `staleMs` 分布与频率监控」——**这是 D4 拍板后才做的告警调参，不在本节预设**。

即：本节的采集规则在 D4 任一结论下都成立，无需为 D4 预留分支。
