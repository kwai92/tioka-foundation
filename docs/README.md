# tioka-foundation 文档导览

本仓库是**独立仓库**。设计权威（S15、ADR-051〜054、ADR-076〜078、F-00〜F-14、36 号规划）存放在 Tioka 主仓库，但单独克隆本仓库时必须能自包含地构建、接入与交付，因此把「接入方需要知道什么」收敛在这里。**本目录下所有链接均为仓库内相对链接，不跨仓库。**

## 1. 先看哪一篇

| 你是 | 先读 | 再读 |
| --- | --- | --- |
| 要接入的新 Java 业务系统（画像 B） | [接入方与能力矩阵](#2-接入方画像) | [接入运行模式](adaptation/runtime-modes.md)、[能力开关矩阵](adaptation/capability-switches.md)、[替换点与不适配清单](adaptation/replacement-points.md)、[契约导览](contracts/README.md)、[降级策略](adaptation/degradation.md) |
| 要接入的非 Java / 遗留系统（画像 C） | [接入方与能力矩阵](#2-接入方画像) | [Node 接入参考实现](../sdk-examples/node/README.md)（可直接拷贝）、[兼容矩阵](compatibility-matrix.md)、[契约导览](contracts/README.md)（版本头 / Webhook / SCIM）、[替换点](adaptation/replacement-points.md) |
| 只要字典、参数、编号的轻量服务（画像 D） | [接入运行模式](adaptation/runtime-modes.md) §3.3 | [替换点与无中间件形态](adaptation/replacement-points.md) §3、[能力开关矩阵](adaptation/capability-switches.md)、[服务端装配 profile](adaptation/server-profiles.md) |
| 要发版或改底座主干 | [版本约定](version-tracks.md) | [契约导览](contracts/README.md)、[兼容矩阵](compatibility-matrix.md)、[适配性原则](#3-适配性原则)、[交付与离线包](delivery/README.md) |
| 要部署 / 多套环境 | [部署命名空间](adaptation/deployment-namespace.md)、`../deploy/README.md` §4.1 | [交付与离线包](delivery/README.md)、`../docs/ops/备份恢复与升级回滚演练记录.md` |

构建、版本发布、迁移号段见仓库根 [`README.md`](../README.md)；**当前唯一 Maven 坐标、改版本时要同步哪几处，见 [版本约定](version-tracks.md)**。

## 2. 接入方画像

Foundation 是底座，不是「所有项目都必须全量接入」的单体。四类画像按不同深度接入：

| 画像 | 代表 | 技术栈 | 接入方式 |
| --- | --- | --- | --- |
| A 核心平台 | Tioka | Java 17 / Boot 3.2 / PG / 全量中间件 | 全量 starter + 中台服务；须保留 `local` 模式可回退 |
| B 新建业务系统 | `lease-biz` 等 | Java 17 / Boot 3.x / 独立部署与库 | OIDC + 按需引 starter；不必拉起全部中间件 |
| C 遗留 / 外购 / 非 Java | 客户既有 OA、Node/.NET 服务 | 任意 | OIDC + OpenAPI + Webhook + SCIM；不引 Java starter、不连 RocketMQ |
| D 轻量服务 / 工具 | 定时任务、同步工具 | 任意，可能无 Redis / 无 MQ | 只取字典 / 参数 / 编号；用 `minimal` 装配与本地 SPI 实现 |

## 3. 适配性原则

1. **契约先行且版本化**：对外接口与事件都有入库契约，破坏性变更只进主版本并保留并行期。
2. **能力可裁剪**：总开关 + 每个能力独立开关已落地；`foundation-server` 支持 `full/iam/common/minimal` 四种装配 profile（[说明](adaptation/server-profiles.md)）。
3. **依赖可替换，替换点在 SDK 侧**：缓存、事件通道、IdP 校验、对象存储、密钥源等 SPI 与本地实现已清点并有自动化自检（[替换点矩阵](adaptation/replacement-points.md)）；**服务端锁定 PG 16 / Redis / RocketMQ / Keycloak 一套栈，不做 MySQL / Kafka / 任意 IdP 的服务端适配**。这不是能力缺失，是刻意划定的边界：让形态不同的业务项目以不同深度接入，而不是让底座迁就每一种栈。
4. **降级可配置**：默认 fail-closed，只读主数据可按白名单使用过期缓存 + 降级标记（[降级策略](adaptation/degradation.md)）；`foundation.client.mode=foundation|local|self-managed` 三模式与不连中台的装配分支已落地（[接入运行模式](adaptation/runtime-modes.md)），自管授权导入器仍属 F2。
5. **配置外部化**：realm / topic / 键前缀 / 桶 / schema 由 `foundation.deployment-id` 派生，显式环境变量优先（[部署命名空间](adaptation/deployment-namespace.md)）；库 schema 与审计链按 id 独立尚未完全落地。
6. **演进可控**：接口清单基线、事件 schema 版本头与兼容三档已入库；[兼容矩阵](compatibility-matrix.md) 把支持边界钉成机器可读契约，并由 CI 按 6 个组合实跑守门。
7. **独立仓库自包含**：单独克隆即可构建、测试、读文档、发版、离线交付（[交付说明](delivery/README.md)）；干净机器自检清单的第 6 步仍需 Docker。

## 4. 第一批已落地（F3，2026-11-15）

| 工作项 | 内容 | 位置 |
| --- | --- | --- |
| ADP-05 | 细粒度能力开关：总开关 + `foundation.<capability>.enabled` | [开关矩阵](adaptation/capability-switches.md)、`foundation-starter-core/condition/` |
| ADP-01 | REST 接口清单冻结基线 + 破坏性变更检测 | `foundation-server/src/test/resources/contracts/api-inventory-v1.txt` |
| ADP-02 | 事件 `schemaVersion`、契约注册表、Webhook（HMAC）双通道 | [契约导览](contracts/README.md) |
| ADP-04 | 权限清单 JSON Schema 与 fail-closed CI 校验 | `docs/contracts/permission-manifest-v1.schema.json` |
| ADP-12 | 独立仓库文档自包含（本文） | 仓库根 `README.md`、`deploy/README.md` |

## 5. 第二批已落地（F3，2026-11-15）

| 工作项 | 内容 | 位置 |
| --- | --- | --- |
| ADP-05 余下 | `foundation-server` 四种装配 profile + 15 个业务域条件装配 | [服务端装配 profile](adaptation/server-profiles.md)、`foundation-server/profile/` |
| ADP-03 | 接口主版本头协商（缺省 v1、不支持返回 406、非法返回 400） | [契约导览](contracts/README.md) §3、`starter-core/contract/`、`starter-web/filter/ApiVersionFilter` |
| ADP-08 部分 | 只读缓存过期兜底 + 明确不可用异常 + 降级标记响应头 + 授权快照 `failMode` 接线 | [降级策略](adaptation/degradation.md) |

## 6. 第三批已落地（F3，2026-11-15）

| 工作项 | 内容 | 位置 |
| --- | --- | --- |
| ADP-07 | `foundation.deployment-id` 派生 realm / topic / 键前缀 / 桶 / schema；server EPP 最低优先级注入、compose/Helm/realm 渲染脚本、20 个测试 | [部署命名空间](adaptation/deployment-namespace.md)、`deploy/init/render-deployment.sh` |
| ADP-06 | 接入侧 SPI 与本地实现清点、不适配清单、无 Redis/无 MQ 的六项自动化自检 | [替换点矩阵](adaptation/replacement-points.md)、`foundation-it-example/.../StandaloneRuntimeTest.java` |
| ADP-12 | 私有仓库 settings 模板与 CI secret 说明、离线制品清单、模板 `{app}` 重命名自检、模板 CI 的 OpenAPI 基线修复、模板死配置键回写 | [交付与离线包](delivery/README.md)、`foundation-template/scripts/verify-rename.sh` |

## 7. 第四批已落地（F3，2026-11-15）

| 工作项 | 内容 | 位置 |
| --- | --- | --- |
| ADP-08 余下（前半） | `foundation.client.mode` 三模式、两套自动配置互斥、配置种子本地实现（dict/param/org/person）、启动期契约提示；默认行为零改动 | [接入运行模式](adaptation/runtime-modes.md)、`starter-core/runtime/`、`starter-client/local/` |
| GA 闸门（无需 Docker 部分） | 四种装配 profile 的能力域冻结基线 + 15 个条件探针验证「profile → 属性 → 装配」链路 | `foundation-server/src/test/resources/contracts/server-profile-domains-v1.txt`、`gate/GaModularityGateTest.java` |
| ADP-06 补强 | 无中间件自检矩阵新增第 7 项：core 能按属性暴露运行模式 Bean | `foundation-it-example/.../StandaloneRuntimeTest.java` |

## 8. 第五批已落地（F3，2026-11-15）

| 工作项 | 内容 | 位置 |
| --- | --- | --- |
| ADP-09（C 类） | Node >= 18 零依赖接入参考实现：版本头出站与响应协商、Webhook HMAC-SHA256 原始字节验签、envelope / `schemaVersion` 校验、`eventType + deliveryId` 幂等、降级标记读取；18 个 `node:test` 用例直接比对 Java 源码与事件契约 | [`sdk-examples/node/`](../sdk-examples/node/README.md)、CI `node-sdk` job |
| ADP-11 | 兼容矩阵机器可读契约 + 静态一致性守门 + CI `compatibility` job（3 模式 × 2 profile = 6 组合，由 `CompatibilityMatrixProbeTest` 真正消费环境注入） | [兼容矩阵](compatibility-matrix.md)、`docs/contracts/compatibility-matrix-v1.json`、`scripts/verify-compatibility-matrix.mjs` |

仍未完成的 F3 项（自管授权导入器属 F2、前端包适配层、完整 OpenAPI 请求/响应字段基线、v2 契约与并行发布期、其余 `GA*GateIT` 的 Docker 端到端部分、干净机器端到端自检、JDK 21 验证）见 Tioka 主仓库 `docs/architecture/implementation/36-Foundation后续开发规划与适配性专项.md` 第 15 节。**F1 债务清零（Tioka 侧零接入、`foundation-ui` 空壳、GF 12 条闸门用例）仍是最高前置，五批 F3 均未触碰 Tioka 主仓库业务代码。**

## 9. 硬约束（继承，不可绕过）

- 单企业独立部署，**禁止 `tenant_id` / `sys_tenant`**（ADR-029 第 60 条）；集团多公司用组织树表达。
- 主键 ULID（`{前缀}_{ulid}`）；时间列 `TIMESTAMPTZ`；跨模块编译期只依赖对方 `-api`。
- 配置前缀统一 `foundation.*`；Flyway 自有历史表，Foundation 库自 V1 起，不占 Tioka 号段。
- 不引入未经 ADR 许可的新中间件。
