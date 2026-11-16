# Foundation 版本分轨（双坐标约定）

> 决策日期：2026-11-16。本文属于 Foundation 仓库自包含文档，不依赖 Tioka 主仓库。

## 1. 为什么分轨

分轨前，`main`（稳定基座）与 `feat/f3-adaptability`（适配性剖面）**共用同一个坐标**
`com.foundation:*:0.1.0-SNAPSHOT`，但二者行为不同：

| 差异点 | `main` | `feat/f3-adaptability` |
| --- | --- | --- |
| `FoundationMigrationAutoConfiguration` 条件 | `@ConditionalOnProperty(foundation.enabled)` | `@ConditionalOnFoundationCapability("data")` → 读 `foundation.data.enabled` |
| 能力开关、服务端 profile、运行时降级、部署命名空间、三运行模式、C 类 Node SDK、兼容矩阵 | 无 | 有（F3 五批共 134 个独有文件） |

这导致本地 m2 里同一个 GAV 对应两套行为，**由「最近一次 `mvn install` 的是哪个分支」隐式决定**，
pom 里完全看不出来。换机器、进 CI、或有人在 `main` 上重新 install，下游行为就会静默变化。
Tioka 侧已有实证：在 `main` 基点 install 后，`FoundationDataContractTest.capabilitySwitchGatesStarterAutoConfiguration`
直接失败，换回 F3 构建才通过（见 Tioka `docs/architecture/module-dependencies.md` §7.6）。

版本分轨的目的不是引入新特性，而是**把这个隐式状态变成 pom 里的显式坐标**：
下游选哪个剖面，写在 `<parent>` 版本里即可，与构建顺序无关。

## 2. 两条版本轨

| 坐标 | 所在分支 | 内容 | 适用对象 |
| --- | --- | --- | --- |
| `0.1.0-SNAPSHOT` | `main` | 稳定基座 + PF-00a / PF-00b 发布 pom 修复；**不含** F3 适配性代码 | 新业务项目基线、不需要 F3 适配能力的接入方 |
| `0.2.0-adapt-SNAPSHOT` | `feat/f3-adaptability` | 稳定基座 + F3 五批全部适配性成果 | 已依赖 F3 行为的下游（**Tioka 当前提交**） |

两轨**不合并**：F3 分支保持独立，`main` 不接收 134 个适配性文件。这样既避免主干被一次性铺满，
也避免把「适配性剖面」强加给只需要稳定基座的业务项目——不同项目按需要选版本即可。

## 3. 版本字面量清单（改版本时必须同步）

Foundation 侧共 4 处：

| 文件 | 字段 | 说明 |
| --- | --- | --- |
| `pom.xml`（foundation-build 聚合器） | `<revision>` | reactor 根版本 |
| `foundation-parent/pom.xml` | `<revision>` | 父工程坐标，子模块 `${revision}` 指向它 |
| `foundation-dependencies/pom.xml` | `<revision>` | BOM 坐标，管理所有 `com.foundation:*` 版本 |
| `foundation-template/pom.xml` | `<parent><version>` | 模板从仓库解析父工程，必须指向本轨版本 |

前三者**必须同值**，否则子模块 parent 坐标与 BOM managed 版本错配。

**不要改**这几类 `0.1.0`：

- `foundation-template/{app}-*/pom.xml` 的 `<version>`、模板聚合器自身 `<version>`：新应用自己的坐标，与 Foundation 版本无关。
- `deploy/helm/*`、`deploy/offline/manifest` 的 `0.1.0`：容器镜像 tag，不是 Maven 坐标。
- `README.md` 中「首版 `0.1.0-SNAPSHOT`」：历史陈述。

下游（Tioka）侧共 2 处，**必须同值**：

- 根 `pom.xml` 的 `<parent>com.foundation:foundation-parent:<版本></parent>`（`<relativePath/>` 从仓库解析）。
- `tioka-dependencies/pom.xml` 的 `<foundation.version>`（导入 `foundation-dependencies` BOM 用）。

## 4. 使用与验证

```bash
# 切到目标轨的分支 / worktree，再安装；不要混着 install
mvn -o install -DskipTests

# 下游 pom 两处版本锁定同一轨后验证
mvn -o test -pl tioka-framework -am -Dtest='Foundation*Test' -Dsurefire.failIfNoSpecifiedTests=false
mvn -o test-compile -pl tioka-server -am
```

两轨在本地 m2 是**不同目录、互不覆盖**，这正是分轨要的效果。但仍要注意：

1. **不要在 `main` 上 `mvn install` 后去跑 Tioka 的 F3 契约测试**：不会覆盖 `0.2.0-adapt-SNAPSHOT`，
   但如果下游 pom 仍指向 `0.1.0-SNAPSHOT`，跑的就是稳定基座语义，行为与预期不符。
2. Foundation 仓库**当前没有配置远程**，双轨只在本地 m2 有意义。配置远程后需分别 `deploy`，
   否则 CI / 其他机器无法解析 `0.2.0-adapt-SNAPSHOT`。这是分轨方案的**配套项，不是可选项**。
3. 将来若决定把两轨合并，`revision` 行必然冲突——合并前先决定保留哪个版本号，并同步下游两处声明。
4. `foundation.client.mode`（starter 侧）与 `tioka.foundation.mode`（应用侧）目前仍无启动期一致性校验，
   与版本轨无关，属独立待办。

## 5. 相关文档

- [接入导览](README.md) · [兼容矩阵](compatibility-matrix.md) · [运行模式](adaptation/runtime-modes.md)
- [契约导览](contracts/README.md) · [交付与离线包](delivery/README.md)
- 规划与执行记录：Tioka 主仓库 `docs/architecture/implementation/36-Foundation后续开发规划与适配性专项.md`
  （本文件保持 Foundation 仓库自包含，不引用主仓库文档作为阅读前置）。
