# 能力开关矩阵（ADP-05）

## 1. 语义

`ConditionalOnFoundationCapability` 用一个条件同时读两个键，**激活 = 总开关为真 且 能力开关为真**：

| `foundation.enabled` | `foundation.<cap>.enabled` | 结果 |
| --- | --- | --- |
| 未配置 / true | 未配置 / true | 启用（与改造前一致，接入方零改动） |
| true | false | 只关闭该能力 |
| false | true 或未配置 | 全部关闭（总开关优先，本地模式用） |

缺省值都是 true。关闭某能力时，其自动配置类、Bean、健康检查项与相关路由**整块不装配**，不留半装配状态。

> 实现见 `foundation-starter-core` 的 `condition` 包。所有 starter 都依赖 core；本批同时补齐了 `foundation-starter-data` 漏引 core 的问题（此前 BAS-24 抽取时未发现）。

## 2. 能力开关

| 能力名 | 配置键 | 默认 | 关闭后失去什么 | 典型关闭场景 |
| --- | --- | --- | --- | --- |
| `core` | `foundation.core.enabled` | true | 根级配置绑定（错误码、ID、UTC 约定） | 极少关闭；关它等于不用底座 |
| `web` | `foundation.web.enabled` | true | 全局异常映射、幂等键过滤、脱敏序列化、OpenAPI 配置 | 纯后台任务服务 |
| `security` | `foundation.security.enabled` | true | 令牌校验、`PrincipalHolder`、`@RequirePermission`、`@DataScope` | `local` / 自管授权模式；关闭后业务代码不得再依赖权限注解 |
| `data` | `foundation.data.enabled` | true | MyBatis-Plus 分页拦截、审计字段填充、迁移命名约定 | 不落库的服务 |
| `mq` | `foundation.mq.enabled` | true | outbox、中继、消费幂等、事件通道 | 不发事件且不订阅事件的服务 |
| `cache` | `foundation.cache.enabled` | true | 快照缓存与本地/Redisson 后端 | 无缓存依赖的轻量服务 |
| `client` | `foundation.client.enabled` | true | 字典/参数/组织/序列号的 Foundation 客户端 | 不查中台公共数据的服务 |
| `audit` | `foundation.audit.enabled` | true | 操作日志上报与本地缓存 | 自有审计体系的系统 |
| `observability` | `foundation.observability.enabled` | true | trace/健康指标/OTel 装配 | 已有统一观测平台的接入方 |

## 3. 子特性开关（原有，保留）

| 配置键 | 默认 | 说明 |
| --- | --- | --- |
| `foundation.cache.backend` | `local` | `local` / `redisson`；后者需 Redisson 在 classpath |
| `foundation.mq.relay.enabled` | false | outbox 中继调度；单测与本地默认不启动 |
| `foundation.mq.webhook.enabled` | false | 并列 Webhook 通道（ADP-02）；启用时 `url` 与 `secret` 必填，缺失即启动失败 |
| `foundation.mq.webhook.allowed-event-types` | 空（全部） | 事件白名单 |
| `foundation.web.request-log-enabled` | false | 请求日志过滤 |

## 4. 四类接入方的典型配置

**A 核心平台（Tioka）**：全部保持默认；`foundation.mq.relay.enabled=true`；按需开 `foundation.mq.webhook.enabled=true` 做双通道。

**B 新建业务系统**：只引需要的 starter；例如只要字典与权限：

```yaml
foundation:
  app-code: lease-biz
  base-url: https://foundation.internal
  audit.enabled: false
  observability.enabled: false
  mq.enabled: false
  cache.backend: local
```

**C 非 Java / 遗留系统**：不引任何 Java starter；走 OIDC + OpenAPI + Webhook。Foundation 服务端侧为订阅方开启 Webhook 通道即可，接入方零 Java 依赖。

**D 轻量服务**：只引 `foundation-starter-core` + `foundation-starter-client`，关闭 `mq` / `cache` / `audit` / `observability` / `security`，不连 Redis 与 RocketMQ。

## 5. 后续批次已交付 / 仍未完成（截至第五批）

**已交付**（本文件第 1〜4 节仍有效）：

- `foundation-server` 四种装配 profile 与 15 个业务域条件装配已在第二批落地，见 [服务端装配 profile](server-profiles.md)。
- 可配置降级（过期缓存兜底、降级标记、授权快照 `failMode` 接线）已在第二批落地，见 [降级策略](degradation.md)。
- 部署命名空间 `foundation.deployment-id` 已在第三批落地，realm / topic / 键前缀 / 桶 / schema 按 id 派生，见 [部署命名空间](deployment-namespace.md)。
- 运行模式 `foundation.client.mode=foundation|local|self-managed`、两套自动配置互斥与本地种子只读实现已在第四批落地，见 [接入运行模式](runtime-modes.md)。
- 兼容矩阵机器可读契约与 6 组合 CI 已在第五批落地，见 [兼容矩阵](../compatibility-matrix.md)。本文件第 2 节的 9 个能力名由矩阵校验脚本扫描 `@ConditionalOnFoundationCapability` 逐项核对。

**仍未完成**：

- 自管授权导入器（`SelfManagedImporter`，属 F2，不是本文件的能力开关）。
- 四种 profile 各自的端到端启动闸门（`GaModularityGateIT`）、四条 `GA*GateIT`，以及 F1 债务清零（Tioka 侧零接入、`foundation-ui` 空壳、GF 12 条闸门用例）。
- C 类 Node 接入示例已交付（第五批），但第二个真实接入方验收仍缺。

以上均按 36 号规划排期，不影响本批开关的使用。
