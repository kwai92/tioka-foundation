# 部署命名空间（ADP-07 / P5）

> 适用对象：要部署 `foundation-server` 的运维方，以及要接入 Foundation 的业务项目。
> 设计依据：36 号规划 §3 P5、§5.2 ADP-07、§6「命名空间」与「硬约束继承」。

## 1. 解决什么问题

同一套 Foundation 镜像在 dev / uat / prod 多套部署（或同一客户两套环境）中共用一套
PostgreSQL、Redis、RocketMQ、MinIO、Keycloak 时，改造前所有资源名都是硬编码字面量：
realm `enterprise`、topic `fdn-events`、键前缀 `fdn:`、桶前缀 `fdn-`、库名 `foundation`。
两套部署会互相读到对方的缓存、消费到对方的事件、把文件写进同一个桶。

`foundation.deployment-id` 把这些名字统一派生，一处配置、全套隔离。

## 2. 派生规则

| 资源 | 配置键 | `default` | `prod-a` |
| --- | --- | --- | --- |
| Keycloak realm | `foundation.keycloak.realm` | `enterprise` | `enterprise-prod-a` |
| OIDC issuer / JWKS | `foundation.security.issuer` / `jwk-set-uri` | `.../realms/enterprise` | `.../realms/enterprise-prod-a` |
| outbox 事件主题 | `foundation.event.topic` | `fdn-events` | `fdn-events-prod-a` |
| 缓存 / 锁 / 限流键前缀 | `foundation.cache.key-prefix` | `fdn:` | `fdn:prod-a:` |
| 对象存储桶 | `foundation.file.storage.bucket` | `fdn-app_foundation` | `fdn-prod-a-app_foundation` |
| 库 schema | `foundation.namespace.db-schema` | `foundation` | `foundation-prod-a` |

`deployment-id` 格式：`^[a-z0-9]([a-z0-9-]{0,29}[a-z0-9])?$`（小写、数字、连字符，1〜31 位，
不以连字符开头/结尾）。非法值在启动阶段 `EnvironmentPostProcessor` 直接抛异常，不会带错命名空间半途启动。

**兼容基线**：`default`（缺省）时所有派生值**逐字等于改造前的字面量**，存量部署与既有单测零改动；
使用缺省值且不在 `test` / `local` profile 时，启动会打 WARN 提示生产显式设置。

## 3. 优先级（谁说了算）

```
显式配置 / 环境变量  >  deployment-id 派生值  >  历史字面量
FOUNDATION_KEYCLOAK_REALM     enterprise-prod-a        enterprise
```

服务端把派生值放进一个 `addLast`（最低优先级）属性源，键为 `foundation.namespace.*`；
`application.yml` 以 `${FOUNDATION_XXX:${foundation.namespace.xxx}}` 引用，因此显式值永远优先。
`jwk-set-uri`、`issuer`、`realm` 由同一个 `DeploymentNamespace` 实例派生，保证令牌 `iss` 校验不会
因三者漂移而失败。

## 4. 怎么用

### 4.1 部署 `foundation-server`（Compose）

```bash
eval "$(bash deploy/init/render-deployment.sh --id prod-a)"
docker compose -f deploy/compose.yml up -d --build
```

脚本生成带 id 的 Keycloak realm 导入文件，并输出上表全部 `export`。详见 `deploy/README.md` §4.1。
Helm 在 `values.yaml` 的 `config.FOUNDATION_DEPLOYMENT_ID` 设置；若保留了显式的
`FOUNDATION_KEYCLOAK_REALM` / `FOUNDATION_EVENT_TOPIC`，必须同步改成带 id 的值或删掉这两行。

### 4.2 接入方业务项目（starter）

只需一个键，无需逐个改前缀：

```yaml
foundation:
  deployment-id: ${FOUNDATION_DEPLOYMENT_ID:default}
  app-code: lease-biz
```

`foundation-starter-core` 的 `CoreAutoConfiguration` 暴露 `DeploymentNamespace` Bean；
`foundation-starter-cache` 在 `foundation.cache.key-prefix` 未显式配置时自动改用
`fdn:{id}:`。业务项目也可以用同名 Bean 覆盖派生规则（`@ConditionalOnMissingBean`）。

服务端与接入方**必须使用同一个 id**，否则接入方会去读另一套命名空间下的缓存 / topic。

## 5. 刻意不适配的边界

- **不是多租户隔离**：不引入任何租户隔离字段或按租户分区（ADR-029 第 60 条、根 README 硬约束）。
  集团多公司用组织树表达（S15 §3.2）。
- **不改服务端技术栈**：PG / Redis / RocketMQ / Keycloak 一套栈不变（36 号规划 P3）。
- **不自动建库 / 建桶 / 建 realm**：脚本只给出命名并生成 realm 导入文件，
  建库、建桶、Flyway 号段仍由部署方按每套部署准备。
- **审计哈希链按 deployment-id 独立**尚未落地（规划 §6 约定），当前同一套库内仍是一条链。

## 6. 实现位置与验证

| 位置 | 作用 |
| --- | --- |
| `foundation-starter-core/.../namespace/DeploymentNamespace.java` | 派生规则、格式校验、`defaults()` 属性 |
| `foundation-starter-core/.../config/FoundationProperties.java` | `foundation.deployment-id` 绑定 |
| `foundation-starter-core/.../config/CoreAutoConfiguration.java` | 暴露 Bean、缺省值告警 |
| `foundation-server/.../profile/DeploymentNamespaceEnvironmentPostProcessor.java` | 注入最低优先级派生属性源 |
| `foundation-server/src/main/resources/META-INF/spring.factories` | 注册 EPP |
| `foundation-server/src/main/resources/application.yml` | realm / topic / issuer / JWKS / 桶 外部化 |
| `foundation-starter-cache/.../FoundationCacheAutoConfiguration.java` | 键前缀派生 |
| `deploy/init/render-deployment.sh` | 按 id 生成 realm 文件与 export |

单测：`DeploymentNamespaceTest`（10 例）、`DeploymentNamespaceEnvironmentPostProcessorTest`（6 例）、
`KeyPrefixNamespaceTest`（4 例）。未覆盖的端到端部分是 `GaReplaceSpiGateIT`
（同一镜像两套 id 同时起，断言库 / 键 / topic / bucket 互不干扰），需 Docker，归 CI。

## 7. 实施中发现并修复的真实缺陷

1. `deploy/README.md` 声明的 `foundation.storage.bucket-prefix` 与 `foundation.redis.prefix`
   **从未被任何代码绑定**；实际生效的是 `foundation.file.storage.bucket` 与
   `foundation.cache.key-prefix`。文档已按代码回写，避免部署方设了不生效的变量。
2. `application.yml` 把 `jwk-set-uri` / `issuer` 里的 realm 写成字面量 `enterprise`，
   与 `foundation.keycloak.realm` 分离；改 realm 时令牌校验会静默失败。现在三者同源派生。
