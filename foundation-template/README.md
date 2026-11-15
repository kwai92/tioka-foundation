# {app} 子系统（BAS-25 工程模板）

本目录是 **BAS-25 工程模板**：一个新子系统的复制起点（**不是代码生成器**）。
从模板复制一份到新目录，改名、接配置、跑自检，即可在 1 天内通过接入自检。

模板基于 BAS-24 的 starter（`foundation-starter-core` / `-security` / `-audit` / `-data` / `-client`），
父工程为 `foundation-parent`，依赖版本全部来自 `foundation-dependencies`（本模板**不写任何版本号**）。

> 本模板刻意**不加入** `tioka-foundation` 根 `pom.xml` 的 `<modules>`：它是复制起点，不是 reactor 模块。
>
> 占位符 **`{app}`** 同时出现在 **目录名、文件名与文件内容**中；复制后用 `scripts/rename.*` 统一替换为新应用编码（如 `contract`）。

## 目录结构

```text
.
├── pom.xml                       # 聚合器（parent = foundation-parent，空 relativePath）
├── {app}-server/                 # Spring Boot 应用装配 + Flyway 迁移 + 运行配置
├── {app}-example-api/            # 示例契约（纯 -api，无额外依赖）
├── {app}-example-biz/            # 示例实现（api/application/domain/config 分层）
├── {app}-ui/                     # Vue 3 + Element Plus 最小骨架
├── docs/权限清单.json             # 权限清单（S15 §11.1）
├── .github/workflows/ci.yml      # CI 模板（S15 §5.3 全部任务）
├── Dockerfile                    # 多阶段镜像构建
├── deploy/helm/{app}/            # Helm 片段
└── scripts/                      # rename / self-check / 权限清单校验
```

## 1. 快速开始（从模板起新子系统）

```bash
# 1) 复制模板到新目录（工程名建议用应用编码，如 contract）
cp -r foundation-template ../contract && cd ../contract

# 2) 改名：目录名 / 文件名 / 文件内容中的 {app} 全部替换（幂等，打印替换文件数）
scripts/rename.ps1 -App contract        # Windows PowerShell
# scripts/rename.sh --app contract      # POSIX bash

# 3) 改 application.yml：DB 连接、FOUNDATION_BASE_URL、服务账号（见下）
# 4) 在 Foundation 登记应用（编码、名称、地址、负责人），取得应用编码与服务账号
# 5) 构建
mvn -B verify
```

`application.yml` 需按环境填写的键（前缀一律 `foundation.*`）：

| 键 | 环境变量 | 说明 |
| --- | --- | --- |
| `spring.datasource.url/username/password` | `APP_DB_URL` / `APP_DB_USERNAME` / `APP_DB_PASSWORD` | 数据库连接 |
| `foundation.base-url` | `FOUNDATION_BASE_URL` | 中台地址（`mode=foundation` 时必填） |
| `foundation.app-code` | `APP_CODE`（默认 `{app}`） | 应用编码 |
| `foundation.deployment-id` | `FOUNDATION_DEPLOYMENT_ID` | 部署命名空间，须与服务端一致（ADP-07） |
| `foundation.service-account.client-id` / `client-secret` / `token-uri` | `FOUNDATION_SERVICE_ACCOUNT_CLIENT_ID` / `_CLIENT_SECRET` / `_TOKEN_URI` | 服务账号（**只写不读**，环境变量注入） |

> 配置前缀就是 `foundation`（`FoundationProperties`），**不是** `foundation.client`；
> 本模板早期版本写的 `foundation.client.*` 没有被任何 `@ConfigurationProperties` 绑定，已按代码回写。

## 2. 六项接入自检（S15 §12.1）

接入完成前逐项确认；`scripts/self-check.sh` 会打印下述指引并执行脚本项（权限清单校验 / `mvn verify` / 前端 build）。

| # | 检查项 | 检查什么 | 通过标准 | 相关配置 |
| --- | --- | --- | --- | --- |
| 1 | **登录** | 门户跳转登录，令牌经 starter 校验并解析出账号、人员、主组织单元 | 进入首页，业务请求不再 401 | `foundation.base-url` / `app-code` / `service-account.*` |
| 2 | **菜单** | 授权快照菜单与前端路由一致；菜单只来自快照 | 只出现已授权菜单；刷新与深链可直达 | `docs/权限清单.json` 的 `menus` |
| 3 | **按钮** | 按钮权限码与接口 `@RequirePermission` 一致 | 无权按钮隐藏；直调接口返回 403 | 清单 `permissions` 与控制器注解 |
| 4 | **数据范围** | 列表按快照的数据范围（部门五档 + 自定义维度）过滤 | 超出范围的数据不可见、不可改 | 清单维度声明 + `foundation-starter-security` 数据范围注解 |
| 5 | **单点注销** | 门户注销后本应用会话同步失效 | 刷新或再次请求回到登录 | starter 会话与快照缓存失效 |
| 6 | **离职联动** | 人员离职后账号停用、待办按移交规则转移 | 会话失效；待办按移交规则转移 | `fdn.account.offboarded` 等事件订阅 |

> 在门户内以子应用运行时，另需自检 **基座内加载、深链、保活切换** 三项（S15 §6.3）。

## 3. 权限清单文件格式（S15 §11.1）

每个应用在仓库维护一份权限清单，随版本发布时由 CI 或启动时导入。**本模板采用 JSON 格式**
（与 Foundation 的导入器 `com.foundation.authz.domain.ManifestParser` 一致；文件为 `docs/权限清单.json`）。

顶层形态固定为：

```json
{ "version": 1, "menus": [ ... ], "permissions": [ ... ] }
```

**menus 每项字段**：

| 字段 | 必填 | 取值 / 说明 |
| --- | --- | --- |
| `code` | 是 | 菜单编码，正则 `^[A-Za-z0-9][A-Za-z0-9_.:-]*$`，清单内唯一；上级以 `parent` 的 code 关联 |
| `name` | 是 | 名称 |
| `type` | 是 | `DIR` \| `MENU` \| `BUTTON` |
| `parent` | 否 | 上级菜单 code |
| `permissionCode` | 否 | 关联权限码（须在 `permissions` 中声明） |
| `routePath` / `component` / `icon` | 否 | 路由路径 / 前端组件标识 / 图标 |
| `sort` | 否 | 排序（整数） |
| `visible` | 否 | 是否显示（默认 `true`） |

**permissions 每项字段**：

| 字段 | 必填 | 取值 / 说明 |
| --- | --- | --- |
| `code` | 是 | 权限码，形如 `资源:动作`，正则 `^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$`，清单内唯一 |
| `name` | 否 | 名称 |
| `riskLevel` | 否 | `LOW` \| `MEDIUM` \| `HIGH`（缺省 `LOW`；高风险授予需二次确认并审计） |

本模板示例：菜单 `{app}.example`（`MENU`，`permissionCode={app}:example:read`）、
按钮 `{app}.example.create`（`BUTTON`，`permissionCode={app}:example:admin`）；
权限码 `{app}:example:read`、`{app}:example:admin`。

**导入规则（清单版本只能前进）**：

- 新增的权限码与菜单直接生效，默认不授予任何角色。
- 清单里消失的权限码标为「已下线」，保留 90 天后清理；已下线的权限码从快照中剔除。
- 同一应用的清单版本只能前进；回滚应用版本时重新导入旧清单视为新版本，审计里可见。

CI 前置校验：`scripts/check-permission-manifest.sh docs/权限清单.json`（用 `python3` 或 `node` 做最小结构校验，无第三方依赖）。

## 4. CI 模板 / Dockerfile / Helm / 接入

### 4.1 CI 模板（`.github/workflows/ci.yml`，覆盖 S15 §5.3 全部任务）

| 任务 | 命令 |
| --- | --- |
| 编译 + 单元测试 + 集成测试（Testcontainers） | `mvn -B clean verify` |
| ArchUnit（规则集来自 `foundation-test`） | `mvn -B test -Dtest=*Arch*` |
| 依赖漏洞扫描（OWASP） | `mvn -B org.owasp:dependency-check-maven:check` |
| OpenAPI 契约比对 | springdoc 生成 `openapi.json` 后与 `docs/openapi.json` diff |
| 权限清单校验 | `scripts/check-permission-manifest.sh docs/权限清单.json` |
| 镜像构建 | `docker build -t {app}:$GITHUB_SHA .` |

Runner：`ubuntu-latest` + Java 17（temurin）+ Maven 缓存。

### 4.2 Dockerfile

多阶段：`maven:3.9-eclipse-temurin-17` 构建（`mvn -B -DskipTests package`）→ `eclipse-temurin:17-jre` 运行，
取 `{app}-server/target/*.jar`，以**非 root** 用户运行，暴露 8080。

```bash
docker build -t {app}:local .
docker run --rm -p 8080:8080 -e FOUNDATION_BASE_URL=http://host.docker.internal:8080 {app}:local
```

### 4.3 Helm 片段（`deploy/helm/{app}/`）

`values.yaml` 提供 `image`、`replicaCount`、`resources`、`env`（含 `FOUNDATION_BASE_URL`、`FOUNDATION_SERVICE_ACCOUNT`、datasource、redis）等。

```bash
helm install {app} deploy/helm/{app} -f deploy/helm/{app}/values.yaml
```

### 4.4 接入 `foundation-starter-*` 与前端包

- 后端：在 `{app}-server/pom.xml` 引入所需 starter（`foundation-starter-core/-security/-audit/-data/-client`），**不写版本**；
  模块实现里用 `@RequirePermission` 声明权限、`@Audited` 上报审计、`PrincipalHolder` 取当前操作者。
- 前端：`{app}-ui` 是最小骨架；正式接入时以 **BAS-FE-01 前端组件包** 替换「登录跳转、按快照菜单生成路由、
  按钮权限指令、字典/组织人员选择器」等实现，并保留基座加载、深链、保活切换。

## 5. 分层与包结构约定

- 模块分层：`api`（对外契约/DTO/事件）、`application`（用例编排）、`domain`（领域规则，不依赖框架）、
  `infrastructure`（持久化/外部系统）、`config`（`@AutoConfiguration` 装配）。示例模块
  `{app}-example-api`（纯契约）+ `{app}-example-biz`（实现）示范 `api` / `biz` 拆分。
- **跨模块编译期只允许依赖对方的 `-api`**：其他模块只引接口，不直接依赖对方的 `-biz`。
- 控制器**不标** `@RestController`：用 `@ResponseBody` + `@RequestMapping`，由 `@AutoConfiguration` 以 `@Bean` 装配。
- 标识与时间：主键形态 `{前缀}_{ulid}`；时间列 `TIMESTAMPTZ`，DO 字段 `LocalDateTime`（UTC 语义）。
- 硬约束：单企业独立部署，**禁止租户维度列**（ADR-029 第 60 条：不引入多租户）。
