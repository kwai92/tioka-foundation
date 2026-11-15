# 兼容矩阵（ADP-11，第五批）

> 一句话：**适配性放在接入侧，服务端锁定一套技术栈**。本页把「支持什么 / 不支持什么 / CI 真的跑了哪些组合」变成机器可读契约，而不是一句口号。

权威源文件：[`docs/contracts/compatibility-matrix-v1.json`](contracts/compatibility-matrix-v1.json)，由 `scripts/verify-compatibility-matrix.mjs` 与 CI 共同守门。改动代码但没改契约，或改了契约但 CI 少跑一档，都会让 PR 变红。

---

## 1. 服务端基线（锁定，不做替换适配）

| 项 | 支持 | 说明 |
| --- | --- | --- |
| JDK | **17** | `foundation-parent/pom.xml` 的 `java.version` 与 `maven.compiler.release` 同源，脚本逐项核对；**21 仅列为待验证**，本机无 JDK 21 时不进 CI（CM-06） |
| Spring Boot | **3.2.12** | 与 `spring-boot.version` 同源 |
| 数据库 | PostgreSQL（pgvector） | Flyway / MyBatis-Plus 依赖 PG 方言 |
| 缓存 | Redis | `foundation.cache.backend=local|redisson` 是**接入侧**开关，不代表服务端可换库 |
| 消息 | RocketMQ | outbox 中继、事件 topic 与 tag 按此实现 |
| 身份 | Keycloak | realm / JWKS / issuer 由 `DeploymentNamespace` 同源派生 |
| 对象存储 | MinIO | bucket 名纳入命名空间派生 |

**明确不适配**（`server.notAdapted`，对应 36 号规划 D2）：MySQL / MariaDB 作为服务端主库、Kafka / RabbitMQ 作为服务端事件总线、非 Keycloak 的 IdP 直接替代服务端发行方、JDK 8/11 运行服务端、Spring Boot 2.x。这不是「暂未做」，是**架构决策**：服务端多栈适配会让迁移、事件契约与授权快照的语义分叉，收益远低于在 SDK / 接入侧做适配。

> 第五批顺带核实了一处部署文档漂移：`foundation.secret.master-key-ref` 无 Java 绑定（`SecretStoreAdapter` 只是 Javadoc 设想），真实生效的是 `foundation.param.master-key` 与 `foundation.org.person.master-key`。已在 `deploy/README.md` 与 compose 注释中就地标注不生效，变量保留兼容、代码未改，详见 [`replacement-points.md`](adaptation/replacement-points.md)。

## 2. 接入侧可适配维度

| 维度 | 取值 | 权威实现 |
| --- | --- | --- |
| 运行模式 `foundation.client.mode` | `foundation` / `local` / `self-managed` | [`runtime-modes.md`](adaptation/runtime-modes.md)；三种模式与服务端 profile **正交**（CM-01） |
| 服务端装配 profile | `full` / `iam` / `common` / `minimal` | [`server-profiles.md`](adaptation/server-profiles.md)；域集合由 `server-profile-domains-v1.txt` 冻结 |
| 能力开关 | 9 个：`core` `web` `security` `data` `mq` `cache` `client` `audit` `observability` | [`capability-switches.md`](adaptation/capability-switches.md)；脚本扫描全部 `@ConditionalOnFoundationCapability` 取值核对 |
| 部署命名空间 | `foundation.deployment-id` | [`deployment-namespace.md`](adaptation/deployment-namespace.md) |
| 降级 | `fail-closed` 缺省 + 只读过期兜底 | [`degradation.md`](adaptation/degradation.md) |
| 非 Java 接入 | Node >= 18（零依赖）、Webhook + REST 版本头 | [`sdk-examples/node/README.md`](../sdk-examples/node/README.md) |

### 组合规则

| 编号 | 规则 |
| --- | --- |
| CM-01 | 运行模式与服务端 profile 正交，互不影响 |
| CM-02 | `foundation.enabled=false` 总开关优先于任何 `foundation.<cap>.enabled` |
| CM-03 | `local` / `self-managed` 下不得存在指向中台的出站 Bean（由 `RuntimeModeAssemblyTest` 断言） |
| CM-04 | 四种 profile 都必须包含 `common` 前置域；`authz` 还依赖 `app` |
| CM-05 | 不提供服务端中间件替换；新增适配必须先改本契约并经 D2 决策 |
| CM-06 | 未经验证的 JDK 版本不得写入 CI 矩阵 |

## 3. CI 真的跑哪些组合

CI `compatibility` job 用 `strategy.matrix` 覆盖 **3 模式 × 2 profile = 6 个组合**：

```
foundation/full        local/full        self-managed/full
foundation/minimal     local/minimal     self-managed/minimal
```

- profile 只取**两端**（全量 / 最小）；`iam` 与 `common` 两档由 `server-profile-domains-v1.txt` 冻结基线 + `GaModularityGateTest` 的 15 个条件探针逐档守卫，不必再占 4×3=12 个 job。
- 每个组合通过 `FOUNDATION_COMPAT_MODE` / `FOUNDATION_COMPAT_PROFILE` 注入 `CompatibilityMatrixProbeTest`，该测试会：**核对组合已登记 → 解析模式与 profile → 用 `ApplicationContextRunner` 装配 `CoreAutoConfiguration` 并断言模式 Bean → 比对该 profile 的能力域基线 → 验证 CM-02**。不是把同一套测试空跑 6 次。
- 本机未注入环境变量时按默认 `foundation/full` 运行，与全量单测行为一致。

```bash
# 本机复现任一组合
FOUNDATION_COMPAT_MODE=local FOUNDATION_COMPAT_PROFILE=minimal \
  mvn -o -q test -pl foundation-server -am \
  -Dtest=CompatibilityMatrixProbeTest -Dsurefire.failIfNoSpecifiedTests=false

# 静态一致性（零依赖）
node scripts/verify-compatibility-matrix.mjs
```

校验脚本会核对：JDK / Spring Boot 版本与 pom、运行模式与 `FoundationRuntimeMode.parse` 分支、profile 与枚举构造值、9 个能力开关与源码注解、锁定栈镜像与 `compose.yml`、CI 两轴与笛卡尔积 6 组合、未验证 JDK 未进 CI、`notAdapted` 非空。

## 4. 新增组合 / 新增适配的流程

1. 先改 `compatibility-matrix-v1.json`（评审兼容边界），再改代码，最后改 CI 两轴与 `combinations`；三者不同步脚本会拒绝。
2. 新增能力开关必须同时补 `@ConditionalOnFoundationCapability` 与矩阵 `capabilities`，否则脚本检出「代码有、契约没有」。
3. 新增 profile 必须同时刷新 `server-profile-domains-v1.txt` 基线（`-Dfoundation.contract.update=true`）并放入 CI 轴或说明由哪条基线守卫。
4. 若要解除某项 `notAdapted`，必须走 36 号规划 D2 决策并给出迁移与双栈语义方案；**不能只改代码**。

## 5. 守门清单

| 守门 | 位置 | 检出的漂移 |
| --- | --- | --- |
| `verify-compatibility-matrix.mjs` | CI `compatibility` job 首步 | 契约 vs 代码 / pom / compose / CI 轴 |
| `CompatibilityMatrixProbeTest` | 6 个 CI 组合 + 本机全量 | 组合未登记、模式 Bean 装配错误、profile 域漂移、CM-02 回归 |
| `GaModularityGateTest` | 主 `build` job | profile 能力域冻结基线被改 |
| `node --test sdk-examples/node/test/client.test.mjs` | CI `node-sdk` job | Java 侧头名 / 签名 / 版本与 Node 示例漂移 |

## 6. 仍未完成

- JDK 21 只在契约里列为 `javaPlannedUnverified`：本机只有 JDK 8/11/17，按 CM-06 不写进 CI；要真正支持需在有 JDK 21 的环境跑通全量并改契约。
- 四种 profile 各自完整启动 `foundation-server` 的 Testcontainers 端到端、其余 `GA*GateIT`、第二个真实接入方验收与 F1 债务清零仍待推进；本页只覆盖**装配与契约层**的矩阵。
- 完整 OpenAPI 请求/响应字段基线、v2 契约与并行发布期尚未提供，见 [`contracts/README.md`](contracts/README.md) §6。
