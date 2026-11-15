# foundation-server 装配 profile（ADP-05）

> 适用范围：**只影响 `foundation-server` 服务端**。业务项目（接入方）永远通过「按需引 starter + 能力开关」裁剪，不需要也不应该配置本文件的 profile。能力开关见 [开关矩阵](capability-switches.md)。

## 1. 为什么需要

改造前 `foundation-server` 无条件引入 15 个业务 `-biz` 模块，启动时全量装配组织、账号、权限、消息、文件、日历、同步、SCIM 等所有域，并连带拉起 PG / Redis / MQ / Keycloak 相关 Bean。只想用字典与编号的轻量接入方（画像 D）因此被迫承担整套中台的启动成本与故障面。

profile 把「服务端装配多少个业务域」变成部署期配置，且**默认行为与改造前完全一致**（`full`）。

## 2. 四种 profile

| profile | 装配的域 | 典型用途 |
| --- | --- | --- |
| `full`（默认） | 全部 15 个域 | A 类核心平台、中台自身、需要 SCIM / 同步 / 消息的部署 |
| `iam` | `common, app, org, account, authz, auth` | 只要组织、账号、权限与授权快照的身份中心 |
| `common` | `common, sequence, audit` | 字典、参数、编号、审计 |
| `minimal` | `common, sequence` | 画像 D：只提供字典、参数、编号 |

配置：

```yaml
foundation:
  server:
    profile: minimal          # 或环境变量 FOUNDATION_SERVER_PROFILE
    domains:
      file: true              # 逐域覆盖 profile 默认值（优先级更高）
```

环境变量 `FOUNDATION_SERVER_PROFILE` 写在 `application.yml` 中；`foundation.server.domains.*` 也可以由环境变量 / 配置中心注入。

## 3. 15 个可裁剪域

| 域 | 装配类 | full | iam | common | minimal |
| --- | --- | :---: | :---: | :---: | :---: |
| `common` | `CommonAutoConfiguration` | ● | ● | ● | ● |
| `app` | `FoundationAppAutoConfiguration` | ● | ● | — | — |
| `org` | `OrgAutoConfiguration` | ● | ● | — | — |
| `account` | `AccountAutoConfiguration` | ● | ● | — | — |
| `authz` | `AuthzAutoConfiguration` | ● | ● | — | — |
| `auth` | `AuthAutoConfiguration` | ● | ● | — | — |
| `audit` | `AuditAutoConfiguration` | ● | — | ● | — |
| `sequence` | `SequenceAutoConfiguration` | ● | — | ● | ● |
| `notify` | `NotifyAutoConfiguration` | ● | — | — | — |
| `file` | `FileAutoConfiguration` | ● | — | — | — |
| `calendar` | `CalendarAutoConfiguration` | ● | — | — | — |
| `sync` | `SyncAutoConfiguration` | ● | — | — | — |
| `scim` | `ScimAutoConfiguration` | ● | — | — | — |
| `feishu` | `FeishuAutoConfiguration` | ● | — | — | — |
| `ldap` | `LdapAutoConfiguration` | ● | — | — | — |

**依赖约束**：`common` 提供 `EventProperties` 等公共 Bean，是 `org / account / authz / notify / sync` 的前置；`app` 提供 `AppApi`，是 `authz` 的前置；`scim` 依赖 `sync` 的 `MasterDataPort` 与 `DiffPreviewService`。所以四种 profile 都含 `common`，`iam` 含 `app`，而 `scim` 只在 `full` 有意义。若需要「iam + SCIM」，显式把 `foundation.server.domains.sync=true` 与 `scim=true` 打开即可，但仍需确认对应 Bean 的依赖已满足。

## 4. 实现机制

1. `FoundationServerProfile`（`foundation-server`）定义四种 profile 与域集合；未知值**快速失败**，不悄悄降级为 full。
2. `FoundationServerProfileEnvironmentPostProcessor` 通过 `META-INF/spring.factories` 注册，在条件求值前把 `foundation.server.domains.<domain>` 以 `MapPropertySource.addLast` 注入为**最低优先级默认值**，因此显式配置总能覆盖。
3. 15 个业务 `AutoConfiguration` 各带 `@ConditionalOnProperty(prefix="foundation.server.domains", name="<域>", havingValue="true", matchIfMissing=true)`。`matchIfMissing=true` 保证不通过 `foundation-server` 启动、单独复用某个 `-biz` 构件时仍按原语义全量装配。
4. 条件不满足时整块自动配置、其 Bean、控制器与定时任务一并下线，不留半装配状态。

`FoundationServerProfileTest` 覆盖：profile 映射、属性注入与覆盖、条件装配联动，以及**全部 15 个装配类都声明了合法且不重复的域条件**（防止以后新增域忘记加注解）。

## 5. 刻意不做的事（边界）

- **Flyway 仍全量执行**：66 个迁移共用一套库结构，四种 profile 部署在同一库规格上；按 profile 拆分迁移位置会让号段分裂、升级路径复杂化。建表不装配 Bean 没有副作用。
- **profile 不自动关闭中间件能力开关**：`foundation.mq.enabled` / `foundation.cache.enabled` 等仍由部署方按 [开关矩阵](capability-switches.md) 显式配置。原因是 `common` 域的事件发布依赖 `OutboxAppender`（由 `starter-mq` 提供），自动关闭可能造成半装配。ADP-06 已清点本地 SPI 实现并提供无 Redis / 无 MQ 的六项自动化自检（见 [替换点矩阵](replacement-points.md)）；「一键最小中间件组合」仍待后续批次。
- **不做路径分版或分库**：profile 只裁剪装配，不改变接口路径，因此 [接口清单基线](../contracts/README.md) 在四种 profile 下是同一份；`minimal` 下未装配的控制器自然不暴露。
- **未跑端到端启动闸门**：本机无 Docker，`GaModularityGateIT`（四种 profile 各自独立启动并断言只暴露声明能力）仍需在 CI 补齐，见 36 号规划 §7。

## 6. 与接入方的关系

| 画像 | 建议 |
| --- | --- |
| A 核心平台 | 服务端 `full`；接入侧按需保留总开关与降级模式 |
| B 新建业务系统 | 通常共用一套 `full` 或 `iam` 服务端；**裁剪靠少引 starter**，不要为每个业务系统起一套不同 profile 的 Foundation |
| C 非 Java | 不关心 profile，走 OpenAPI / Webhook / SCIM；若 SCIM 必需，服务端须为 `full` 或打开 `sync/scim` |
| D 轻量服务 | 可部署一套 `minimal` 服务端，或干脆不引 starter、只按契约调用已有 Foundation 的字典/编号接口 |
