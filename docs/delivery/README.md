# 独立仓库交付与离线包（ADP-12）

> 目标（36 号规划 §7 `GaSelfContainedIT`）：**在一台只克隆了 `tioka-foundation` 的干净机器上**，
> 按 README 完成构建、测试、发到本地仓库、起最小部署、从模板起新子系统并通过自检。
> 本文件补齐规划 §11、§12 之后剩下的最后一块：私有仓库凭据、CI secret、离线制品、模板自检。

## 1. 干净机器自检清单

| # | 步骤 | 命令 | 通过标准 |
| --- | --- | --- | --- |
| 1 | JDK 17 | `java -version` | 17.x |
| 2 | 全量构建 + 单测 | `mvn -o -q test`（首次需联网拉依赖，去掉 `-o`） | 全绿，无 ERROR |
| 3 | 契约与清单守门 | `bash foundation-template/scripts/check-permission-manifest.sh docs/contracts/permission-manifest-example.json` | exit 0 |
| 4 | 发到本地仓库 | `mvn -B -DskipTests install` | 各模块 `BUILD SUCCESS`，`.flattened-pom.xml` 不含 `${revision}` |
| 5 | 部署命名空间派生 | `eval "$(bash deploy/init/render-deployment.sh --id uat)"` | 9 条 `export`，realm 文件生成 |
| 6 | 起最小部署 | `docker compose -f deploy/compose.yml up -d --build`（需 Docker） | `/actuator/health/readiness` 就绪 |
| 7 | 模板改名自检 | `bash foundation-template/scripts/verify-rename.sh`（见 §4） | 无 `{app}` 残留、清单校验通过 |
| 8 | 无中间件接入自检 | `mvn -o -q -pl foundation-it-example -am test -Dtest=StandaloneRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false` | 6 项全绿 |

第 6 步需要 Docker；本机无 Docker 时按既有约定归 CI 判定，其余 7 步均可在纯 JDK 环境完成。

## 2. 私有 Maven 仓库与凭据

`foundation-parent/pom.xml` 的 `distributionManagement` 用 `${env.FOUNDATION_REPO_URL}`，
仓库 id 为 `foundation-private`。把 [settings.xml.template](settings.xml.template) 复制/合并到
`~/.m2/settings.xml`，凭据由环境变量注入：

| 环境变量 | 用途 | 注入位置 |
| --- | --- | --- |
| `FOUNDATION_REPO_URL` | 私有仓库 releases/snapshots 地址 | 环境变量 → `distributionManagement` |
| `FOUNDATION_REPO_USER` | 仓库账号 | `settings.xml` 的 `${env.*}` |
| `FOUNDATION_REPO_TOKEN` | 仓库令牌/密码 | `settings.xml` 的 `${env.*}` |

**绝不**把这三个值写进仓库文件、镜像层或 CI 日志。GitHub Actions 里配置为 Repository Secrets，
在 `deploy` job 中以 `env:` 注入即可（当前主 CI 只做 `verify`，发版 job 由负责人按 D6 决策后添加）。

> 36 号规划第 9 节 **D6**（私有 Maven/npm 仓库地址、离线包是否含构件 tar）仍待负责人拍板；
> 本文件提供的是可直接套用的模板与流程，地址与制品形态未定案。

## 3. CI secret 与离线制品清单

### 3.1 CI 必需 secret

| secret | 何时需要 | 用途 |
| --- | --- | --- |
| `FOUNDATION_REPO_URL` / `USER` / `TOKEN` | 发版 job | 见 §2 |
| `FOUNDATION_PARAM_MASTER_KEY` | 起服务端（生产） | 参数加密主密钥，缺失启动失败 |
| `FOUNDATION_KEYCLOAK_SECRET` | 起服务端 | 服务账号 client_secret |
| `FDN_OWNER_PASSWORD` / `FDN_APP_PASSWORD` | 迁移与运行 | PG 属主/运行账号口令 |
| `FOUNDATION_DEPLOYMENT_ID` | 多套部署 | 命名空间标识，见 [部署命名空间](../adaptation/deployment-namespace.md) |

### 3.2 离线交付包应包含

`deploy/offline/manifest` 是**唯一**镜像清单（约束 F43，不在别处重复登记版本）。除镜像外还需附：

| 制品 | 来源 | 说明 |
| --- | --- | --- |
| 服务与前端镜像 tar | `docker save` 按 `deploy/offline/manifest` 导出 | 版本逐行对齐清单 |
| 全量 Maven 构件 | `mvn -B -DskipTests deploy` 后打包仓库目录 | 或离线 `repository/` tar（D6 待定） |
| 迁移 SQL | `foundation-server/src/main/resources/db/migration` | Flyway 属主账号执行 |
| Keycloak realm 导入 | `deploy/init/foundation-realm.json`；非 default 用 `render-deployment.sh` 生成的 `foundation-realm.<id>.json` | realm 名必须与服务端派生值一致 |
| 契约基线 | `docs/contracts/`、`foundation-server/src/test/resources/contracts/api-inventory-v1.txt`、`foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json` | 接入方自检与破坏性变更检测 |
| 部署配置 | `deploy/compose.yml`、`deploy/helm/foundation/`、`deploy/README.md` | 按 `FOUNDATION_DEPLOYMENT_ID` 生成各套环境变量 |

离线清单本身与 `deployment-id` 无关（镜像只有一份），但 realm / 桶 / 库名按 id 生成，
命令见 [部署命名空间](../adaptation/deployment-namespace.md) §4.1。

## 4. 模板 `{app}` 重命名自检

`foundation-template/scripts/verify-rename.sh` 在**临时目录**里完成：复制模板 → `rename.sh --app sample`
→ 扫描 `{app}` 残留 → 权限清单校验 → 打印结果；不会修改仓库内的模板本身。
主 CI 的「契约与清单校验」步骤已调用它，模板改名后能否通过自检不再靠人工确认。

模板 CI（`foundation-template/.github/workflows/ci.yml`）同时修复了两处「必然失败」：

1. **OpenAPI 基线不存在**：原步骤 `diff -u docs/openapi.json openapi.json` 在模板里没有基线文件，
   首次接入必然失败。现改为「基线存在才比对，不存在则打印指引并跳过」；
   接入方按 [契约导览](../contracts/README.md) §2 生成并提交基线后自动启用严格比对。
2. **启动服务取 springdoc 产物**：`npx wait-on` + `curl` 在无数据库/中间件的 CI runner 上起不来。
   现在只在基线存在时才执行，且失败即明确报错，不再静默通过。

## 5. 仍未闭环（对应 36 号规划 §11/§12 的遗留项）

- `GaSelfContainedIT` 端到端（干净机器 + Docker + 新子系统 1 天过自检）未自动化，需 CI 环境。
- `foundation-ui` 仍是空壳，离线包里的 `foundation/foundation-ui` 镜像尚无真实前端产物（F1 债务）。
- 私有仓库地址、npm 仓库、构件 tar 是否入离线包（D6）待拍板。
- 模板 README 中 `foundation.client.base-url` / `app-code` 与实际绑定键
  `foundation.base-url` / `foundation.app-code` 不一致，已在本批回写（见 §6）。

## 6. 本批修掉的模板配置死键

`foundation-template/README.md` 原写 `foundation.client.base-url`、`foundation.client.app-code`、
`foundation.client.service-account`，但 `foundation-starter-core` 的 `FoundationProperties`
前缀就是 `foundation`，实际键为 `foundation.base-url`、`foundation.app-code`、
`foundation.service-account.*`。照原文档配置会静默不生效。已按代码回写模板 README 与
`{app}-server/src/main/resources/application.yml`。
