# Foundation 部署运行手册（BAS-21 / F-11 §3）

Foundation 独立仓库的部署交付物：Docker Compose（小部署 / 验证）与 Helm chart（生产），
外加数据库与 Keycloak 初始化脚本、离线镜像清单。

- 施工设计权威：[F-11 部署与 Tioka 接入与 GF 闸门](../../docs/architecture/construction/F-11-部署与Tioka接入与GF闸门.md) §3
- 设计权威：S15 Foundation 基础平台系统 §16
- 本仓库是 Foundation 独立仓库（ADR-051 第 1 条）；本目录对应 F-11 §3.2 中 `compose.foundation.yml`
  叠加剖面的**自包含等价物**，不新增任何未经 ADR 许可的中间件（只用 PostgreSQL / Redis / RocketMQ / MinIO / Keycloak）。

## 1. 目录结构

```
deploy/
├── compose.yml                  # Foundation 自包含剖面（含中间件、Keycloak、Foundation、管理台）
├── Dockerfile.foundation        # Foundation 服务镜像（同一父工程 + OTel agent）
├── ui/                          # 管理台/门户静态资源（占位页，正式 SPA 由 BAS-FE-* 提供）
│   ├── Dockerfile
│   └── index.html
├── init/
│   ├── 01-pgvector.sql          # CREATE EXTENSION vector
│   ├── foundation-db.sql        # 建 foundation/keycloak 库与 fdn_owner/fdn_app 角色
│   └── foundation-realm.json    # Keycloak realm import（realm enterprise）
├── helm/foundation/             # Helm chart（服务、管理台、迁移 Job、Keycloak、Ingress、ConfigMap/Secret、HPA）
├── offline/manifest             # 离线镜像清单登记（约束 F43）
└── README.md                    # 本文件
```

## 2. 一条命令起全套（Compose）

前置：本机 Docker；服务镜像需要先构建出可执行 jar（compose 的构建上下文是仓库根）。

```powershell
# 1) 构建 Foundation 服务 jar（在仓库根执行；首次需联网拉依赖）
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'; $env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn -B -pl foundation-server -am package -DskipTests

# 2) 一条命令起全套
docker compose -f deploy/compose.yml up -d --build
```

访问：

| 单元 | 地址 |
| --- | --- |
| 管理台 / 门户（占位） | http://localhost:8080 |
| Foundation 服务 | http://localhost:8081 |
| Foundation 存活探针 | http://localhost:8081/actuator/health/liveness |
| Foundation 就绪探针 | http://localhost:8081/actuator/health/readiness |
| Keycloak（issuer） | http://localhost:8180/realms/enterprise |
| MinIO 控制台 | http://localhost:9001 |

### 启动顺序（compose `depends_on` + `condition` 表达）

`keycloak`（`service_healthy`）→ `foundation-migrate`（Flyway，`service_completed_successfully`）→
`foundation-server`（等迁移成功且 postgres/redis/keycloak 健康）→ `foundation-ui`（等 server 健康）。

- 迁移由**独立容器** `foundation-migrate`（镜像 `flyway/flyway:10-alpine`）执行；
  应用内 Flyway 关闭（`FOUNDATION_FLYWAY_ENABLED=false`）。
- Keycloak 启动较慢，探针为 10s 间隔 / 12 次重试（与 F-11 §3.10 对齐）。

## 3. 账号分离（migrate 用属主，运行用业务账号）

初始化脚本 `init/foundation-db.sql` 建立两个库与两个账号（口令可用环境变量覆盖）：

| 账号 | 用途 | 权限 |
| --- | --- | --- |
| `fdn_owner` | 执行 Flyway 迁移（`foundation-migrate`） | 库属主，`public` schema 属主，可 DDL |
| `fdn_app` | 应用运行时（`foundation-server`） | `CONNECT`；审计类表**只有 INSERT、SELECT**，其余业务表按需 |

口令注入：`FDN_OWNER_PASSWORD`、`FDN_APP_PASSWORD`（compose 默认 `fdn_owner_dev` / `fdn_app_dev`，**生产必须覆盖**）。
`keycloak` 库由 Keycloak 自身 schema 管理，Foundation 不写该库。

## 4. 配置项 ↔ 环境变量（`foundation.*`，Compose 与 Helm 共用同一套键名）

下表为 `foundation-server/src/main/resources/application.yml` 实际引用的键；Spring Boot 宽松绑定
使 `foundation.keycloak.server-url` ↔ `FOUNDATION_KEYCLOAK_SERVER_URL`，但本仓库对若干键使用了显式占位符，
**以「映射环境变量」列为准**。

| 配置键 | 映射环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `server.port` | `FOUNDATION_SERVER_PORT` | `8081` | 服务端口 |
| `spring.datasource.url` / `foundation.db.url` | `FOUNDATION_DB_URL` | `jdbc:postgresql://localhost:5432/foundation` | Foundation 库地址 |
| `spring.datasource.username` / `foundation.db.username` | `FOUNDATION_DB_USERNAME` | `foundation` | 运行账号（生产 `fdn_app`） |
| `spring.datasource.password` / `foundation.db.password` | `FOUNDATION_DB_PASSWORD` | `foundation` | 运行账号口令 |
| `spring.datasource.hikari.maximum-pool-size` | `FOUNDATION_DB_POOL_MAX` | `20` | 连接池上限 |
| `spring.flyway.enabled` / `foundation.flyway.enabled` | `FOUNDATION_FLYWAY_ENABLED` | `true` | 迁移由独立 Job 执行时置 `false` |
| `spring.data.redis.host` | `FOUNDATION_REDIS_HOST` | `localhost` | Redis 主机 |
| `spring.data.redis.port` | `FOUNDATION_REDIS_PORT` | `6379` | Redis 端口 |
| `spring.data.redis.database` | `FOUNDATION_REDIS_DATABASE` | `0` | Redis 库号 |
| `foundation.redis.prefix` | `FOUNDATION_REDIS_PREFIX` | `fdn` | 键前缀 |
| `foundation.keycloak.server-url` | `FOUNDATION_KEYCLOAK_URL` | `http://localhost:8180` | Keycloak 基址（管理接口） |
| `foundation.keycloak.realm` | `FOUNDATION_KEYCLOAK_REALM` | `enterprise` | 单一 realm |
| `foundation.keycloak.client-id` | `FOUNDATION_KEYCLOAK_CLIENT_ID` | `foundation-admin` | 服务账号 client_id |
| `foundation.keycloak.client-secret` | `FOUNDATION_KEYCLOAK_SECRET` | 空 | 服务账号密钥（生产必填） |
| `foundation.mq.name-server` | `FOUNDATION_MQ_NAME_SERVER` | — | RocketMQ NameServer 地址 |
| `foundation.event.topic` | `FOUNDATION_EVENT_TOPIC` | `fdn-events` | 事件主题（前缀 fdn） |
| `foundation.storage.endpoint` | `FOUNDATION_STORAGE_ENDPOINT` | — | 对象存储地址 |
| `foundation.storage.bucket-prefix` | `FOUNDATION_STORAGE_BUCKET_PREFIX` | `fdn-` | bucket 前缀 |
| `foundation.server.base-url` | `FOUNDATION_SERVER_BASE_URL` | — | 对外地址（回调与深链） |
| `foundation.sso.public-url` | `FOUNDATION_SSO_PUBLIC_URL` | — | OIDC issuer，须与令牌 `iss` 完全一致 |
| `foundation.authz.snapshot-cache-ttl` | `FOUNDATION_AUTHZ_SNAPSHOT_CACHE_TTL` | `5m` | 快照缓存 TTL（S15 §11.3） |
| `foundation.audit.retention-days` | `FOUNDATION_AUDIT_RETENTION_DAYS` | `180` | 在线保留天数（SEC 类见 ADR-072） |
| `foundation.secret.master-key-ref` | `FOUNDATION_SECRET_MASTER_KEY_REF` | — | 来自 `SecretStoreAdapter` |
| `foundation.param.master-key` | `FOUNDATION_PARAM_MASTER_KEY` | 空 | 参数加密主密钥，**生产缺失启动失败** |
| `foundation.dict.cache-ttl` | `FOUNDATION_DICT_CACHE_TTL` | `5m` | 字典缓存 TTL |
| `foundation.app.default-auth-mode` | `FOUNDATION_APP_DEFAULT_AUTH_MODE` | `CONCENTRATED` | 应用默认授权模式 |

> 部署面（`FOUNDATION_SERVER_BASE_URL`、`FOUNDATION_SSO_PUBLIC_URL`、`FOUNDATION_MQ_NAME_SERVER`、
> `FOUNDATION_STORAGE_*`、`FOUNDATION_AUTHZ_SNAPSHOT_CACHE_TTL`、`FOUNDATION_AUDIT_RETENTION_DAYS`、
> `FOUNDATION_SECRET_MASTER_KEY_REF`、`FOUNDATION_REDIS_PREFIX`）在 compose 中已给出 dev 默认，
> 生产必须覆盖为真实值。

## 5. 单点登录域名与证书（F-11 §3.6）

| 项 | 设计 |
| --- | --- |
| 域名 | `sso.<domain>` → Keycloak（issuer）；`foundation.<domain>` → Foundation 服务与管理台/门户 |
| realm | `enterprise`；issuer = `https://sso.<domain>/realms/enterprise`，须与令牌 `iss` 完全一致 |
| 回调 | 各应用的 redirect URI 由 BAS-04 登记应用时自动开通 OIDC 客户端，不在证书运维里手工维护 |
| 证书 | TLS 在 Ingress / 网关终止；生产用客户 CA 或 cert-manager；集群内可 HTTP，**对外必须 HTTPS**（S15 §15） |

Helm：`deploy/helm/foundation` 的 `ingress.ssoHost` / `ingress.foundationHost` 与 `ingress.tls` 配置上表。
`deploy/init/foundation-realm.json` 的 `foundation-admin` 客户端 `secret` 为占位值，生产导入后必须轮换并写入 Secret。

## 6. 无状态与跨实例会话（F-11 §3.7）

| 组件 | 会话处理 |
| --- | --- |
| Foundation 服务 | 无状态、多副本；缓存与锁在 Redis（键前缀 `fdn`）；单实例任务（同步、对账）靠 Redis 锁互斥 |
| Keycloak | 会话与 realm 缓存用其内置分布式缓存（Infinispan），≥2 副本共享，无需额外中间件 |
| 可用性 | Foundation 不在请求热路径：应用靠快照缓存运行，短时不可用不影响已登录用户；Keycloak 不可用时新登录失败，已有会话在令牌有效期内可用 |

## 7. 备份与恢复（F-11 §3.8）

Foundation 库与 Keycloak 库纳入备份方案；**恢复要求两库时间点一致**，恢复后再跑一次账号对账（BAS-03）补齐偏差；
对象存储（审计归档、文件服务）纳入同一口径；主密钥导出经 KMS 包裹。

```powershell
# 备份（属主账号）
pg_dump -Fc -U fdn_owner -h $env:PGHOST -d foundation -f "foundation-$(Get-Date -Format yyyyMMdd).dump"
pg_dump -Fc -U fdn_owner -h $env:PGHOST -d keycloak   -f "keycloak-$(Get-Date -Format yyyyMMdd).dump"
```

演练记录模板见 [docs/ops/备份恢复与升级回滚演练记录.md](../../docs/ops/备份恢复与升级回滚演练记录.md)
（当前为**模板，尚未演练**；`BackupRestoreDrill` 演练在具备 Docker 环境后执行）。

## 8. 升级与回滚（F-11 §3.9）

- 迁移只向前；同一里程碑内遵守「先扩后收」——新版本加列与新表，旧版本代码能在新库上运行。
- 删列、删表、收权限只在里程碑边界做，并要求先备份。
- 回滚 = 应用回到上一版本镜像；跨破坏性迁移的回滚只能从备份恢复。

```powershell
# 升级
helm upgrade --install foundation deploy/helm/foundation -n foundation --create-namespace `
  -f deploy/helm/foundation/values-prod.yaml --wait
# 迁移 Job 完成后应用才启动
kubectl -n foundation wait --for=condition=complete job/foundation-migrate --timeout=10m
```

## 9. 健康检查与就绪探针（F-11 §3.10）

| 探针 | 端点 | 通过条件 |
| --- | --- | --- |
| 存活 | `/actuator/health/liveness` | 进程可用 |
| 就绪 | `/actuator/health/readiness` | DB 可连、Redis 可连、Keycloak 管理接口可达、事件与快照组件就绪 |
| Keycloak | `/health/ready`（Keycloak 内置） | 数据库与缓存就绪 |
| 中间件 | compose `healthcheck` | PostgreSQL `pg_isready`、Redis `PING`、Keycloak TCP 8180 |

- 迁移 Job 是应用的启动前置：Job 未完成前应用不就绪。
- Foundation 侧探针由 `spring-boot-starter-actuator` 提供；Keycloak 可达性由 `KeycloakHealthIndicator`
  （bean 名 `keycloak`）通过 `KeycloakAdminClient.ping()` 判断。

## 10. 可观测性（F-11 §3.11）

复用 M4 OBS-11 的 OTel Collector（尾部采样）、Loki、Prometheus；Foundation 服务名 `foundation`：

- 镜像内置 OTel Java Agent 1.33.5（`JAVA_TOOL_OPTIONS=-javaagent:...opentelemetry-javaagent.jar -Duser.timezone=UTC`）。
- `OTEL_SERVICE_NAME=foundation`、`OTEL_TRACES_EXPORTER=otlp`、`OTEL_METRICS_EXPORTER=none`、`OTEL_LOGS_EXPORTER=none`。
- 关键指标：登录成功/失败、快照取用与降级拒绝、同步任务、`fdn-events` outbox 积压与死信、
  `audit.chain.unsealed`/`mismatch`、Keycloak 管理接口成功率与超时。
