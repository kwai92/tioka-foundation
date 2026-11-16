# 运行时降级策略（ADP-08，第一批）

> 默认 **fail-closed**：Foundation 不可用且无有效缓存时明确失败，不返回陈旧数据、更不静默返回 `null`。本文是「中台临时故障」时的降级策略；第四批落地的 `foundation.client.mode=local|self-managed` 是「根本不接中台」的运行模式，两者是独立路径，见 [接入运行模式](runtime-modes.md)。

## 1. 三条规则

1. **只读主数据**（字典 / 参数 / 组织 / 人员）：可按能力配置为「过期缓存兜底 + 降级标记」。
2. **身份与授权**：默认且建议保持 `deny`。显式配置 `stale` 才允许在陈旧上限内放行，且必须留审计日志与降级标记。
3. **写接口**（编号取号、文件上传、删除等）：**不降级、不兜底**，直接失败，避免重复与非幂等副作用。

## 2. 配置项

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `foundation.client.degrade.policy` | `fail-closed` | 全局策略；`stale-cache` 才允许使用过期缓存 |
| `foundation.client.degrade.max-stale` | `30m` | 过期缓存可容忍的最大陈旧时长，超过即拒绝兜底 |
| `foundation.client.degrade.capabilities` | `dict,param,org,person` | 允许降级的能力白名单；**白名单外一律 fail-closed** |
| `foundation.authz.snapshot.fail-mode` | `deny` | 授权快照策略；`stale` 为过期放行 |
| `foundation.authz.snapshot.stale-max-age` | `10m` | 授权快照陈旧上限 |
| `foundation.authz.snapshot.cache-ttl` | `5m` | 快照缓存 TTL（既有配置） |
| `foundation.client.mode` | `foundation` | 接入运行模式；`local` / `self-managed` 下不装配中台客户端，详见 [接入运行模式](runtime-modes.md) |
| `foundation.client.local.enabled` | `false` | 独立模式下是否启用配置种子实现（dict/param/org/person） |

`Degrade.policyFor(capability)` 是唯一判定出口：即使全局打开 `stale-cache`，`security` / `authz` 等不在白名单的能力仍返回 `FAIL_CLOSED`。配置拼写错误（如 `fail-open`）会**启动期快速失败**，不会被当成 deny 悄悄放过。

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

## 4. 授权快照的等保边界

`SnapshotCache` 原先把「缓存过期且回源失败」写死为 `FORBIDDEN`（F-07 §4 / S15 §11.3）。本批把 `FoundationAuthzProperties.Snapshot.failMode` 真正接线：

- `deny`（默认）：行为与改造前完全一致，由 `SnapshotDegradeTest` 覆盖。
- `stale`：仅在存在过期快照且未超 `stale-max-age` 时放行；同时写审计 WARN 日志并打降级标记；`load()` 与 `version()` 走同一出口。
- 确定性业务拒绝（`BizException`）不降级，避免把 403 掩盖成过期放行。

> **待决策 D4**：36 号规划建议「身份与授权类不允许 fail-open」。本批按「默认 deny、可配但需显式开启且留痕」实现，是保守但可回退的形态。若 D4 拍板禁止，只需把 `isStaleAllowed()` 固定为 `false` 并删除 `stale` 分支，不影响其它能力。**生产环境在 D4 拍板前不应启用 `stale`。**

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

## 6. 仍未完成

- **自管授权导入器**（ADP-08 余下部分）：三模式与配置种子本地实现已于第四批落地（[接入运行模式](runtime-modes.md)），`SELF_MANAGED` 目前只有模式与装配位置；`SelfManagedImporter` 属 F2 范围，未实现。
- Tioka 侧 `tioka.foundation.mode` 与 starter 侧 `foundation.client.mode` 的一致性联动（F1 接线时补启动期校验）。
- 若干 `Http*Client`（日历 `workday` / `addWorkdays` / `diffWorkdays`、编号 `current`）在服务端返回空响应时仍返回 `null`；本批未改动，因为它们属于**业务空结果**而非「服务不可用」，需要在契约层明确定义后才能统一改成 `Optional` 或异常。
- 降级事件写入审计中心（目前仅结构化 WARN 日志）、告警通道接入、以及 `GaDegradeGateIT`（Foundation 停机 10 分钟的端到端验证）。
- 缓存降级目前只覆盖进程内 `TtlCache`；引入 `foundation-starter-cache` 的 Redis 实现后需提供同构的过期兜底语义（ADP-06）。
