# 契约导览（ADP-01 / 02 / 03 / 04）

所有对外契约都**入库且随构件发布**，接入方不需要读 Java 代码就能对接；CI 对契约做 fail-closed 守门，破坏性变更不能无声合入。

| 契约 | 权威文件 | 消费方 | 守门方式 |
| --- | --- | --- | --- |
| 事件契约（事件名 / schemaVersion / 通道形态 / envelope 字段） | `foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json`（随 jar 发布） | Java 与非 Java 订阅方 | `EventSchemasContractTest` + CI 文件存在性检查 |
| REST 接口清单 | `foundation-server/src/test/resources/contracts/api-inventory-v1.txt` | 全部接入方 | `ApiInventoryContractTest` 逐字比对 |
| 接口主版本 | `foundation-starter-core/.../contract/FoundationApiVersion.java`（常量，随 jar 发布） | 全部接入方 | `ApiVersionFilterTest`、`ApiVersionHeaderTest` + ADP-01 清单基线 |
| 权限清单 JSON Schema | `docs/contracts/permission-manifest-v1.schema.json` | 应用登记方、模板工程 | CI 调用 `check-permission-manifest.sh` 校验示例清单 |
| 兼容矩阵（ADP-11） | `docs/contracts/compatibility-matrix-v1.json` | 全部接入方、发版与适配边界评审 | `scripts/verify-compatibility-matrix.mjs` 比对代码 / pom / compose / CI；`CompatibilityMatrixProbeTest` 在 6 个 CI 组合中实跑 |

## 1. 兼容性规则

| 变更 | 分类 | 处理 |
| --- | --- | --- |
| 新增接口 / 事件类型 / payload 字段 / 可选 HTTP 头 | 兼容 | 刷新基线并在发版说明登记；不升主版本 |
| 新增 `X-Foundation-Api-Version` 请求/响应头 | 兼容（ADP-03） | 旧接入方不带该头视为 v1，零改动 |
| 删除或改名接口、改 HTTP 方法或路径 | 破坏性 | 只进主版本，保留一个并行发布期 |
| 删除或改语义事件字段、改 envelope 顶层字段、改签名算法或签名头 | 破坏性 | 同上 |
| `schemaVersion` 递增 | 破坏性 | 新版本另开契约文件（`-v2.json`），旧版本继续并行投递 |

`DomainEvent.schemaVersion` 缺省为 1，四参数工厂 `DomainEvent.of(...)` 保持兼容；`EventSchemas.versionOf(eventType)` 按注册表解析，未登记的新事件回落到 v1（前向兼容），但契约测试会要求补登记。

## 2. 两条事件通道

| | MQ | Webhook |
| --- | --- | --- |
| 开关 | `foundation.mq.relay.enabled` | `foundation.mq.webhook.enabled` |
| 形态 | topic `fdn-events`，tag = eventType，body = payload JSON | POST envelope JSON |
| 元数据 | tag + payload 内保留字段 `schemaVersion`、`deliveryId` | 顶层字段 + `X-Fdn-*` 头 |
| 签名 | 无（内网） | `X-Fdn-Signature: sha256=<HMAC-SHA256(secret, raw_body)>` |
| 适用 | Java / 有 RocketMQ 的接入方 | 非 Java、无 MQ、跨网络的接入方 |

Webhook envelope 顶层必需字段：`schemaVersion`、`eventType`、`aggregate`、`deliveryId`、`payload`；P1 起新增**可选**字段 `sentAt`（epoch millis）。payload 内的 `schemaVersion` / `deliveryId` 会被提升到顶层，不重复出现；`deliveryId` 若与 outbox 行主键不一致则拒绝投递。

**幂等要求**：中继按「MQ → 各 EventSink」顺序投递，任一通道失败整条转 outbox 重试/死信，因此消费方必须按 `eventType + deliveryId` 幂等。P1 起 `MybatisOutbox` 把 outbox 行主键写入 payload 保留字段 `deliveryId`（业务 payload 不能伪造），MQ 与 Webhook 两条通道共用同一投递标识；`DeliveryAwareIdempotentConsumer` 提供标准去重实现，存量按 `messageId` 去重的消费者行为不变。**不要按 MQ messageId 去重**——中继重试会产生新的 messageId。

**防重放（P1）**：`sentAt` 位于被 HMAC 覆盖的原始 body 内，不可被篡改；刻意不新增时间戳头，签名串仍是 `raw_body`，v1 接入方忽略该字段即可。验签通过后按 `sentAt` 做时间窗校验（Node SDK `assertReplayWindow`），再按 `eventType + deliveryId` 幂等，即构成完整防重放。老中继没有 `sentAt` 时校验明确返回「未启用」，不抛错，保留并行发布期兼容。

**配置**：启用 Webhook 时 `foundation.mq.webhook.url` 与 `secret` 必填，缺失即启动失败（安全默认值，不允许无签名投递）。生产环境 url 必须为 HTTPS。

## 3. 接口主版本协商（ADP-03）

- 头名 `X-Foundation-Api-Version`，当前主版本 **v1**（常量 `FoundationApiVersion.CURRENT_MAJOR=1`）。
- 所有 `foundation-starter-client` 出站请求自动带 `X-Foundation-Api-Version: 1`；服务端 `ApiVersionFilter` 校验并回写同一响应头。
- **请求头缺省视为 v1**，旧接入方与历史脚本零改动；显式声明 `v2` 等不支持的主版本返回 **406** + `FOUNDATION_API_VERSION_UNSUPPORTED`；格式非法返回 **400** + `FOUNDATION_API_VERSION_MALFORMED`。都不会落到 500。
- 采用「版本头 + 网关路由」，**不做 `/v1` `/v2` 路径分版**：路径分版会破坏 ADP-01 冻结的 181 条接口清单，也会让所有接入方改 URL。并行发布期由网关按版本头把流量导向不同实例，旧 starter 在期内保持可用。
- 主版本只在破坏性变更时递增；届时新开 `api-inventory-v2.txt` / `openapi-v2.yaml`，旧版本保留一个并行发布期。当前**尚未提供 v2 契约文件与并行期路由规则**，属 ADP-03 余下部分。

## 4. 刷新接口清单基线

新增接口是兼容变更，但仍需显式评审并刷新冻结基线：

```bash
mvn -pl foundation-server test -Dtest=ApiInventoryContractTest -Dfoundation.contract.update=true
git add foundation-server/src/test/resources/contracts/api-inventory-v1.txt
```

`ApiInventoryContractTest` 扫描 `com.foundation` 下全部 REST 控制器（含 `@RestController`、`@ResponseBody` + `@RequestMapping`、由 AutoConfiguration 显式 `@Bean` 装配的三种形式），输出排序后的 `HTTP方法 + 路径` 清单。默认模式逐字比对，删接口 / 改路径 / 改方法都会让 CI 变红。

## 5. 权限清单

应用登记时导入的清单固定形态为 `{"version":1,"menus":[...],"permissions":[...]}`：

- 菜单编码 `^[A-Za-z0-9][A-Za-z0-9_.:-]*$`；权限码 `^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$`（至少 `资源:动作` 两段）。
- 菜单引用的 `permissionCode` 必须在 `permissions` 中声明；编码不得重复。
- `docs/contracts/permission-manifest-example.json` 是合法示例，也是 CI 的校验输入。
- `foundation-template/docs/权限清单.json` 含 `{app}` 占位符，须先执行 `rename.sh / rename.ps1` 替换后才能通过校验。
- 校验脚本 `check-permission-manifest.sh` 在缺 python3 / node 时**失败退出**，不再静默跳过（ADP-04）。Foundation 侧 `ManifestParser` 是第二道关口，不替代 CI 前置守门。

## 6. 未完成

- 完整 OpenAPI 文档（springdoc 运行时生成）尚未固化为可下载的 `openapi-v1.yaml`；当前 v1 基线是接口清单，能检出「删 / 改」类破坏性变更，但不含请求与响应字段。
- v2 契约文件、并行发布期路由规则与「旧 starter 连新服务端」的端到端回归用例尚未提供；当前只有主版本头的协商与拒绝语义。
- JSON Schema 目前用于文档与人工校验，尚未接入自动化 schema 校验器（CI 只跑等价的结构校验脚本）。
- 接入方契约测试脚手架（把接入方自己的调用灌成回归用例）仍待补；兼容矩阵 CI（ADP-11）已在第五批落地，见 [兼容矩阵](../compatibility-matrix.md)，但**它验证的是底座自身组合，不代替第二个真实接入方的端到端验收**。
- C 类 Node 参考实现（ADP-09）已把 REST 版本头与 Webhook 验签固化为可测试代码，见 `sdk-examples/node/README.md`；它同样不是 OpenAPI 字段基线的替代品。
