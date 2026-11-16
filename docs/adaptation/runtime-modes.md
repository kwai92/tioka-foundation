# 接入运行模式（ADP-08，第四批）

> 一句话：**`foundation.client.mode` 决定业务项目「连不连中台」**；默认是 `foundation`，与改造前逐字一致。切到 `local` / `self-managed` 后，中台只读客户端整套不再装配，因此不会出现「配了本地模式、进程却仍在偷偷连 Foundation」的半装配状态。

本篇与 [服务端装配 profile](server-profiles.md) 是两件事，勿混淆：

| 维度 | 键 | 作用对象 | 决定什么 |
| --- | --- | --- | --- |
| 接入运行模式 | `foundation.client.mode` | **接入方应用**（各 starter） | 要不要连中台、只读客户端用哪套实现 |
| 服务端装配 profile | `foundation.server.profile` | **foundation-server 自身** | 中台服务装配哪些业务域 |

---

## 1. 三种模式

| 模式 | 中台出站客户端 | 授权来源 | 适用画像 | 典型场景 |
| --- | --- | --- | --- | --- |
| `foundation`（默认） | 装配 `Http*Client` + OkHttp + 服务账号令牌 | 中台授权快照 | A / B | 正常接入；存量项目零改动 |
| `local` | **不装配** | 应用自建 | A（回退）/ D | 中台停机回退、Tioka 双模式、纯私有化部署 |
| `self-managed` | **不装配** | 接入方自管 | C | 外购 / 非 Java 系统自行管理授权；C 类 REST / Webhook 对接见 [Node 接入参考实现](../../sdk-examples/node/README.md) |

`LOCAL` 与 `SELF_MANAGED` 共用同一套「不连中台」装配分支，区别在语义与后续演进：前者是**临时回退 / 完全单飞**，后者是**长期自管授权**（授权导入器属 F2，见第 6 节）。

未配置该键时按 `foundation` 处理；值支持大小写、`self-managed` 与 `self_managed` 两种写法；**拼错会启动期快速失败**，不会静默回落到 `foundation` 而意外连上中台。

## 2. 装配互斥

```
                       foundation.client.mode
        ┌──────────────────────┼──────────────────────────┐
    foundation            local / self-managed       local / self-managed
                        (local.enabled 缺省 false)  (local.enabled=true)
        │                     │                          │
FoundationClientAuto-  两套都不装配：              LocalFoundationClientAuto-
Configuration           接入方按 @ConditionalOn-    Configuration
（OkHttp + Http*Client  MissingBean 自行注册        （配置种子：dict / param /
  + 令牌 + 降级缓存）    本地实现）                  org / person）
```

- 两套自动配置靠 `@ConditionalOnFoundationRuntimeMode` 互斥，并同时受 `@ConditionalOnFoundationCapability("client")` 约束；`foundation.enabled=false` 或 `foundation.client.enabled=false` 时**都不装配**，总开关优先。
- `core` 在启动时暴露 `FoundationRuntimeMode` Bean，并由 `FoundationRuntimeModeGuard` 打印当前模式；若切到独立模式却没提供任何本地实现，会明确 WARN 列出需要补齐的 `DictClient / ParamClient / OrgClient / PersonClient`。**守卫刻意不 fail-fast**：无法从 core 层判断接入方是否自己注册了这些 Bean，贸然拒绝会误伤。
- 本地实现用 `@ConditionalOnMissingBean`，接入方自定义实现始终优先于配置种子。

## 3. 配置示例

### 3.1 默认（无需任何配置）

```yaml
foundation:
  app-code: lease-biz
  base-url: https://foundation.internal
```

### 3.2 完全不连中台，由应用自己提供实现（推荐给 A 类回退）

```yaml
foundation:
  client:
    mode: local
```

此时四个只读接口没有默认 Bean，应用侧注册自己的实现即可。启动时会有 WARN 提示，属预期行为。

### 3.3 零代码、纯配置起一个轻量服务（D 类 / 演示 / 离线交付）

```yaml
foundation:
  app-code: edge-tool
  client:
    mode: local
    local:
      enabled: true
      dict:
        gender:
          - item-code: M
            item-name: 男
            sort: 1
          - item-code: F
            item-name: 女
            sort: 2
      param:
        pageSize: "20"
      secret-params:
        - dbPassword
      org-units:
        - id: org-root
          code: root
          name: 总部
          sort: 0
        - id: org-a
          code: dept-a
          name: 部门A
          parent-id: org-root
          sort: 1
      persons:
        - id: per_1
          employee-no: E001
          name: 张三
          primary-org-unit: dept-a
          status: ACTIVE
```

- 字典项按 `sort` 排序，本应用项（`app-code`）优先，与 `HttpDictClient` 语义一致；不存在的类型返回空列表（业务空结果，不是故障）。
- `secret-params` 中的键一律返回空，延续 F-02 §BAS-09「敏感参数只写不读」；**不要把真实密钥写进配置文件**。
- 组织按 `parent-id` 建树，`PersonClient.byOrgUnit(code, true)` 会递归展开整棵子树；`children(code)` 只返回直接下级。
- 本地种子是**只读进程内视图**，不回源、不订阅事件、不引 Redis / RocketMQ / PG，可直接用于无中间件部署。

## 4. 与 Tioka 双模式的关系（F1 已接线）

Tioka 侧的 `tioka.foundation.mode=local|foundation` 是**应用级**开关（F-02 BAS-26 / F-11 BAS-20），本键是**starter 级**契约。两者**同源派生、不再各自配置**：

- Tioka `tioka-server/src/main/resources/application.yml` 把两个键都写成 `${TIOKA_FOUNDATION_MODE:local}`，缺省均为 `local`。此前应用侧缺省 `local`、starter 缺省 `FOUNDATION`，语义正好相反，会出现「应用以为单飞、starter 仍连中台」。
- 一致性由 Tioka 主仓库 `tioka-framework` 的 `FoundationModeConfigConsistencyTest` 守门：校验两键同源、两个解析器对共享取值结果一致、以及空串不得被当成「未配置」而静默回落到连中台。
- 部署侧只需设置 `TIOKA_FOUNDATION_MODE`；不要单独改其中一个键。
- 两个解析器的取值集合仍不完全相同：starter 侧支持 `foundation|local|self-managed`，Tioka 应用侧只支持 `local|foundation`；注入 `self-managed` 会在 Tioka 启动期显式失败，而不是静默按 `local` 运行（自管授权导入器属 F2，尚未接线）。

## 5. 测试与守门

| 测试 | 断言 |
| --- | --- |
| `FoundationRuntimeModeTest` | 三值宽松解析、缺省兼容基线、非法值拒绝、独立模式判定 |
| `FoundationRuntimeModeGuardTest` | 缺省无告警、未开启种子时提示补齐实现、`self-managed` 额外声明、总开关遮蔽 |
| `FoundationRuntimeModeConditionTest` | 条件注解在四种取值下与实际 `Environment` 对齐，非法模式启动失败 |
| `RuntimeModeAssemblyTest` | 缺省装配 HTTP 客户端；独立模式未开种子时两套都不装配；开启种子后只读客户端来自配置且**不建连中台**；总开关优先；自定义实现优先 |
| `LocalFoundationDataTest` | 字典排序与应用项优先、敏感参数过滤、组织树递归展开、空配置不抛异常 |
| `StandaloneRuntimeTest` 自检 7 | 无中间件上下文中 core 能按属性暴露模式 Bean |

## 6. 仍未完成 / 刻意不做

1. **自管授权导入器未实现**：`SELF_MANAGED` 本批只落地模式与装配位置，让 C 类接入方有一等公民的配置入口；`SelfManagedImporter` 与 `@RequireAppGrant` 属 F2（W90+，见 36 号规划 D9），本批不提前写占位实现。
2. **本地模式不提供写能力**：文件、日历、编号、消息等客户端在独立模式下没有 Bean。这些能力有非幂等副作用，底座不提供「假实现」；需要时必须接入中台或由应用自行实现。
3. **降级与本地模式是两条独立路径**：`stale-cache` 过期兜底解决「中台临时故障」，`local` 解决「根本不接中台」；切换模式不会自动打开过期兜底，见 [降级策略](degradation.md)。
4. **`GaDegradeGateIT`**（停机 10 分钟端到端）与 `GaModularityGateIT` 的完整启动部分仍需 Docker，归 CI；`GaModularityGateTest` 已把其中不需要 Docker 的装配裁剪与基线冻结部分落地。
