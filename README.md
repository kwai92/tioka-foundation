# tioka-foundation

Tioka Foundation —— 企业级「Java 工程化底座 + 通用企业中台」的独立仓库。

- 设计权威：Tioka 主仓库的 S15 与 ADR-051〜054；**单独克隆本仓库时以 [文档导览](docs/README.md) 为准**（本仓库内链接全部自包含）
- 施工设计：Tioka 主仓库 F-02 起；接入侧需要知道的约定已收进 [文档导览](docs/README.md)
- 硬约束：单企业独立部署，**禁止 `tenant_id` / `sys_tenant`**（ADR-029 第 60 条）；主键 ULID（`{前缀}_{ulid}`）；时间列 `TIMESTAMPTZ`；跨模块编译期只依赖对方 `-api`；配置前缀 `foundation.*`

## 1. 仓库结构

| 目录 | 职责 | 工作项 |
| --- | --- | --- |
| `foundation-dependencies` | BOM：统一版本清单（只做 `dependencyManagement`） | BAS-23 |
| `foundation-parent` | 父工程：编译参数、静态检查、依赖收敛、发布配置 | BAS-23 |
| `foundation-it-example` | 示例工程：验证「只引父工程即可编译」 | BAS-23 |
| `foundation-starter-*` | 九类 starter（core/web/security/data/mq/cache/observability/audit/client） | BAS-24 |
| `foundation-test` | Testcontainers 容器基座 + ArchUnit 规则集 | BAS-24 |
| `foundation-server` | 装配应用（Spring Boot 入口、运行配置、Flyway 迁移） | BAS-01 |
| `foundation-common/` | 通用公共域聚合器（下含 `-api` / `-biz`） | BAS-01 / BAS-09 |
| `foundation-ui` | Vue 3 + Element Plus 管理台与门户 | BAS-FE |

## 2. 构建

```bash
# 本机默认 JAVA_HOME 可能是 JDK 8，构建前切到 JDK 17
export JAVA_HOME="C:/Program Files/Java/jdk-17"

mvn -B clean test            # 编译 + 单元测试（不起容器，最快）
mvn -B clean verify          # 编译 + 单元测试 + 集成测试（*IT，需 Docker）
mvn -B clean verify -DskipITs  # 同上但跳过集成测试
mvn -B clean verify -Pgate   # GF 闸门用例（@Tag("gate") 的 *IT，需 Docker）
```

集成与闸门用例基于 `foundation-test` 的单例容器（PostgreSQL 16 / Redis 7 / RocketMQ 懒启动 / Keycloak 24），首次运行需拉取镜像。

## 2.1 接入前先读

Foundation 承担底座角色，四类接入方（核心平台 / 新建 Java 业务系统 / 非 Java 遗留系统 / 轻量服务）按不同深度接入，不是全量拷贝：

- [文档导览](docs/README.md)：接入方画像、适配性原则、硬约束
- [能力开关矩阵](docs/adaptation/capability-switches.md)：`foundation.enabled` 总开关与 `foundation.<capability>.enabled` 细粒度开关
- [契约导览](docs/contracts/README.md)：事件契约、接口清单基线、权限清单 schema 与兼容规则

**适配边界**：服务端锁定 PG 16 / Redis / RocketMQ / Keycloak 一套栈，不做 MySQL / Kafka / 任意 IdP 的服务端适配；栈不同的业务项目通过 SDK 侧 SPI、OIDC + OpenAPI + Webhook 接入。

## 3. 版本与发布

- **版本占位**：所有 `pom.xml` 使用 `${revision}`，由 `flatten-maven-plugin` 在 `process-resources` 期固化为具体版本后写入 `.flattened-pom.xml`；安装/发布的 pom 不含 `${revision}`。
- **单轨版本（2026-11-16 收敛）**：`main` 为唯一版本轨 `0.2.0-SNAPSHOT`（含 F3 适配性成果与逻辑修复）；旧 `0.1.0-SNAPSHOT` 已由 tag `pre-single-track-20261116` 归档，不再发布。改版本须同步三处 `<revision>` 与模板 parent，下游须同步根 pom parent 与 `foundation.version`，见 [`docs/version-tracks.md`](docs/version-tracks.md)。
- **版本号规则**（语义化，S15 §5.3）：
  - 首版 `0.1.0-SNAPSHOT`；
  - 破坏性变更只进**主版本**（`X.0.0`）；新增能力进次版本（`X.Y.0`）；修复进修订号（`X.Y.Z`）；
  - 每次发版附**升级说明**（变更点、破坏性变更、迁移指引）。
- **发布流程**：
  1. 改 `pom.xml` 的 `<revision>` 为目标版本（如 `0.1.0`）；
  2. `mvn -B clean deploy`；
  3. 私有仓库地址与凭据经环境变量 `FOUNDATION_REPO_URL` 与 `~/.m2/settings.xml` 的 `foundation-private` server 注入，**不入库**；
  4. 发版后把 `<revision>` 升到下一个 `-SNAPSHOT`。

## 4. 迁移号段

- Foundation 库自 **V1** 起，使用独立 Flyway 历史表，**不占用** Tioka 号段（Tioka 自 V220 起、F 阶段 V270+）；
- 迁移脚本位于 `foundation-server/src/main/resources/db/migration`（Foundation 库），命名 `V{n}__{desc}.sql`；
- 每个工作项的号段在 Tioka 主仓库对应施工设计（F-02 起）中登记；本仓库内以实际合入顺序为准，冲突时以先合入的 PR 为准。F3 适配性专项建议用 V100 起（与 F 阶段 V1〜V50、F2 V200+、Tioka V270+ 互不冲突），见 [文档导览](docs/README.md)。
