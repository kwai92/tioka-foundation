# Foundation 版本约定（单轨）

> 决策日期：2026-11-16（收敛）。本文属于 Foundation 仓库自包含文档，不依赖 Tioka 主仓库。

## 1. 当前唯一版本轨

| 项 | 值 |
| --- | --- |
| Maven 坐标 | `com.foundation:*:0.2.0-SNAPSHOT` |
| 代码分支 | `main`（唯一主线） |
| 内容 | 稳定基座 + PF-00a / PF-00b 发布 pom 修复 + F3 适配性五批全部成果 + ADP-08/ADP-03 逻辑修复 |
| 下游 | Tioka 主仓库根 `pom.xml` 与 `tioka-dependencies/pom.xml` 同锁此版本 |

`0.2.0-SNAPSHOT` 是**唯一**会被构建与发布的坐标。所有 `pom.xml` 使用 `${revision}`，由
`flatten-maven-plugin` 在 `process-resources` 期固化，安装/发布的 pom 不含 `${revision}`。

## 2. 为什么从双轨收敛

2026-11-16 曾为隔离「稳定基座」与「适配性剖面」把版本分成 `0.1.0-SNAPSHOT`（`main`）与
`0.2.0-adapt-SNAPSHOT`（`feat/f3-adaptability`）。分轨的初衷是解决**同一个 GAV 对应两套行为**
的隐患：本地 m2 里哪个分支最后 `mvn install`，下游就拿到哪套语义，pom 里完全看不出来
（Tioka 的 `FoundationDataContractTest` 因此出现过实际失败）。

但 F3 全量验证通过后，双轨的代价超过收益：下游要理解两条版本线、两处 pom 与注释互相矛盾、
改一次版本要同步多个坐标，且「稳定基座」并不比合并后的 `main` 更稳定。因此按产品化要求收敛：

- `af5fd6c`：`git merge --no-ff feat/f3-adaptability` 合入主干，唯一冲突是
  `foundation-dependencies/pom.xml` 的 `<revision>` 行；
- 合并后统一为 `0.2.0-SNAPSHOT`，删除 `-adapt` 后缀（它不再是「剖面轨」）；
- 旧 `main`（`6cca930` / `0.1.0-SNAPSHOT`）由 tag `pre-single-track-20261116` 归档，
  完整历史另存 Tioka 侧 `.workbuddy/backups/foundation-pre-single-track-20261116.bundle`；
- 历史 tag `f3-*-20261116`、`pf00a-regression-fix-20261116` 保留作恢复点。

`0.2.0-adapt-SNAPSHOT` 与 `0.1.0-SNAPSHOT` **均已废弃**，不再构建、不再发布，也不应出现在下游 pom。

## 3. 版本字面量清单（改版本时必须同步）

Foundation 侧共 4 处：

| 文件 | 字段 | 说明 |
| --- | --- | --- |
| `pom.xml`（foundation-build 聚合器） | `<revision>` | reactor 根版本 |
| `foundation-parent/pom.xml` | `<revision>` | 父工程坐标，子模块 `${revision}` 指向它 |
| `foundation-dependencies/pom.xml` | `<revision>` | BOM 坐标，管理所有 `com.foundation:*` 版本 |
| `foundation-template/pom.xml` | `<parent><version>` | 模板从仓库解析父工程，必须指向当前版本 |

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
# 在 main 上安装；单轨后不再有「选哪条轨」这一步
mvn -o install -DskipTests
mvn -o test

# 下游两处版本同步后验证
mvn -o test -pl tioka-framework -am -Dtest='Foundation*Test' -Dsurefire.failIfNoSpecifiedTests=false
mvn -o test-compile -pl tioka-server -am
```

注意：

1. Foundation 仓库**当前没有配置远程**，本阶段按约定只在本地 m2 使用；将来配置远程后只需
   `deploy` 一条版本线，不再需要分别发布两条轨。
2. 升级版本时按 §3 同步 6 处（Foundation 4 + 下游 2），并重跑全量单测、Node SDK 自检、
   兼容矩阵脚本与下游契约测试。
3. `foundation.client.mode`（starter 侧）与 `tioka.foundation.mode`（应用侧）由
   `application.yml` 的 `${TIOKA_FOUNDATION_MODE:local}` **同源派生**，一致性由
   `FoundationModeConfigConsistencyTest` 守门，与版本轨无关。

## 5. 相关文档

- [接入导览](README.md) · [兼容矩阵](compatibility-matrix.md) · [运行模式](adaptation/runtime-modes.md)
- [契约导览](contracts/README.md) · [交付与离线包](delivery/README.md)
- 规划与执行记录：Tioka 主仓库 `docs/architecture/implementation/36-Foundation后续开发规划与适配性专项.md`
  （本文件保持 Foundation 仓库自包含，不引用主仓库文档作为阅读前置）。
