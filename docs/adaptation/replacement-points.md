# 接入侧替换点与「不适配清单」（ADP-06 / P3）

> 适用对象：要在**不装全套中间件**的前提下接入 Foundation 的 B/D 类业务项目，
> 以及非 Java / 外购的 C 类系统。设计依据：36 号规划 §3 P3、§5.2 ADP-06、§6「替换点边界」。

## 1. 边界先行：适配性 ≠ 无限兼容

**服务端锁定一套栈**：PostgreSQL 16 + Redis + RocketMQ + Keycloak 24（S15 §4、F-11「不引入新中间件」）。
Foundation **不提供** MySQL / Kafka / 其他 IdP 的服务端实现，也不替换 Flyway 迁移方言。

**适配层在接入侧**：契约、开关、SPI、降级、命名空间。形态不同的业务项目按不同深度接入：

- **A 核心平台**：全量 starter + 全量中间件；
- **B 新建业务系统**：按需引 starter，用下面第 2 节的本地实现替掉不需要的中间件；
- **C 非 Java / 外购**：不引 Java starter，走 OIDC + OpenAPI 只读查询 + Webhook 事件 + 自管授权；
- **D 轻量服务**：只取字典 / 参数 / 编号，全程本地实现，不连 Redis / MQ / PG。

这条边界对应 36 号规划第 9 节 **D2**，本批按「否（不做服务端适配）」落地；若负责人改口，
需走 ADR-030 变更流程并重估 ADP-06 工作量。

## 2. 替换点矩阵

下表每一行都是代码里**真实存在**的 SPI（`@ConditionalOnMissingBean` 或条件装配），
业务项目提供同名 Bean 即可覆盖，不需要改底座主干。

| 能力 | SPI / 配置点 | 本地 / 缺省实现 | 可替换为 | 切换方式 | 画像 |
| --- | --- | --- | --- | --- | --- |
| 分布式锁 | `DistributedLock` | `LocalDistributedLock`（`backend=local`，缺省） | `RedissonDistributedLock` | `foundation.cache.backend=redisson` | B/D |
| 限流 | `RateLimiter` | `LocalRateLimiter`（缺省） | `RedissonRateLimiter` | 同上 | B/D |
| 键前缀 | `KeyPrefix` | 由 `DeploymentNamespace` 派生 `fdn:{id}:` | 业务方自定义 | `foundation.cache.key-prefix` 或同名 Bean | 全部 |
| 事件投递 | `MqTemplate` | `LoggingMqTemplate`（类路径无 RocketMQ 时） | `RocketMqMqTemplate` | 类路径存在 `RocketMQTemplate` 自动切 | B/D |
| 事件订阅通道 | `EventSink` | RocketMQ（`MqTemplate`） | `WebhookEventSink`（HMAC-SHA256、白名单、重试） | `foundation.mq.webhook.enabled=true` | C |
| outbox | `Outbox` / `OutboxAppender` | `MybatisOutbox`（有 `SqlSessionFactory`） | 业务方实现 | 同名 Bean | B |
| 消费去重 / 幂等 | `ConsumedStore`、`IdempotencyStore` | `InMemoryConsumedStore`、`InMemoryIdempotencyStore` | DB / Redis 实现 | 同名 Bean | B/D |
| 令牌校验 | `PrincipalResolver`、`TokenExtractor` | `JwtPrincipalResolver`、`RequestHeaderTokenExtractor` | `OpaqueTokenPrincipalResolver`、自定义 IdP 校验器 | `foundation.security.token-source` + 同名 Bean | B/C |
| 授权快照 | `AuthzSnapshotClient`、`AuthzSnapshotFetcher` | `HttpAuthzSnapshotFetcher` + `SnapshotCache` | 自管授权（实现 `AuthzSnapshotClient`） | 同名 Bean | C |
| 数据范围 | `DataScopeFilterProvider` | `DefaultDataScopeFilterProvider` | 业务方规则 | 同名 Bean | B |
| 对象存储 | `ObjectStorageAdapter` | `LocalStorageAdapter`（`kind=LOCAL`，缺省） | `S3StorageAdapter`（MinIO / 客户 OSS） | `foundation.file.storage.kind=S3` | B/D |
| 病毒扫描 | `VirusScanHook` | `NoopVirusScanHook` | 客户扫描引擎 | 同名 Bean | B |
| 审计归档 | `AuditArchiveStore` | 在线库归档 | 客户归档系统 | 同名 Bean | B |
| 通知渠道 | `NotificationChannel` | `FeishuNotificationChannel`、`MailNotificationChannel` | 客户渠道 | 同名 Bean | B |
| 同步源 | `SyncSourceAdapter` | 飞书 / LDAP 适配器 | 客户数据源 | 同名 Bean | B |
| 降级策略 | `DegradePolicy` | 只读白名单「过期缓存 + 降级标记」，身份授权强制 `FAIL_CLOSED` | 业务方策略 Bean | `foundation.client.degrade.*` | A/B |
| 密钥源 | `foundation.param.master-key`、`foundation.file.signing-secret` | 环境变量注入 / 进程内随机 | KMS、Vault | 环境变量或同名 Bean | B |

> **文档与代码不一致：第五批已核实**。`deploy/README.md` 与 compose 声明的
> `foundation.secret.master-key-ref`（称来自 `SecretStoreAdapter`）在仓库 Java 代码中
> **没有任何绑定类或接口**，`SecretStoreAdapter` 仅出现在 `ParamCipher` / `PersonCipher`
> 的 Javadoc 设想里。实际生效的是两组配置：`foundation.param.master-key`
> （`ParamProperties` → `AesGcmParamCipher`）与 `foundation.org.person.master-key`
> （`OrgPersonProperties` → `PersonCipher`）。已在 `deploy/README.md` 与 compose 注释中
> 就地标注「配置不生效」，**保留环境变量仅为兼容既有部署脚本**，本批未改 Java 代码、
> 未删除变量。是否补 `SecretStoreAdapter` 实现或彻底清理，需设计侧拍板（与 D2 适配边界相关）。
> 同类残留：`FOUNDATION_REDIS_PREFIX`、`FOUNDATION_STORAGE_BUCKET_PREFIX` 也无绑定，
> 缓存键前缀由 `foundation.deployment-id` 派生，完整桶名由 `foundation.file.storage.bucket` 绑定。

## 3. 最小无中间件形态（D 类）

```yaml
foundation:
  enabled: true
  app-code: edge-tool
  deployment-id: ${FOUNDATION_DEPLOYMENT_ID:default}
  core.enabled: true
  client.enabled: true
  cache.enabled: true
  cache.backend: local          # 不连 Redis
  mq.enabled: false             # 不连 RocketMQ，也不启用 outbox relay
  security.enabled: false       # 自管身份（C/D 类）
  data.enabled: false           # 不连 PG、不跑 Flyway
  file.storage.kind: LOCAL      # 不连 MinIO
```

按需只引 `foundation-starter-core` / `client` / `cache` 三个 starter，
再用 `foundation.<capability>.enabled=false` 关掉其余（见 `capability-switches.md`）。
字典 / 参数 / 编号这类只读能力在 Foundation 不可用时由 `TtlCache.getWithDegrade` 走过期缓存兜底，
见 `degradation.md`；**注意 `foundation.client.mode=local|self-managed` 三模式尚未落地**，
当前只能做到「故障降级」，做不到「完全脱网启动」。

## 4. 六项接入自检（可自动化部分）

`foundation-it-example/src/test/java/.../StandaloneRuntimeTest.java` 用
`ApplicationContextRunner` 装配 core + cache + mq，不启动任何容器，断言：

1. `DistributedLock` 是 `LocalDistributedLock`；
2. `RateLimiter` 是 `LocalRateLimiter`；
3. `MqTemplate` 是 `LoggingMqTemplate`；
4. 上下文中没有 `RedissonClient` / `RocketMQTemplate` Bean；
5. 未设 deployment-id 时键前缀仍是 `fdn:`（兼容基线）；
6. 设置 `foundation.deployment-id` 后键前缀自动带上隔离段，两套 id 不串。

浏览器侧六项（登录 / 菜单 / 按钮 / 数据范围 / 单点注销 / 离职联动）仍由
`foundation-template/scripts/self-check.sh` 逐项人工确认，见 `docs/contracts/README.md`。

## 5. 未交付 / 归 GA 闸门

- `GaReplaceSpiGateIT`：无 Redis、无 RocketMQ 的 **B 类完整工程**（含登录与授权）跑通六项自检，
  以及同一镜像两个 `deployment-id` 同时起、库 / 键 / topic / bucket 互不干扰的端到端验证（需 Docker，归 CI）。
- `foundation.client.mode=local|self-managed` 三模式与自管授权导入（ADP-08 余下）。
- 非 Java 最小客户端示例（Node 校验 Webhook 签名）、OIDC/OpenAPI 三类接入说明（ADP-09）。
- 兼容矩阵与四类接入样例的例行回归（ADP-11）。
