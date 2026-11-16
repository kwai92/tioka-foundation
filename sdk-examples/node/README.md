# C 类接入参考实现：Node.js（ADP-09）

面向**不引 Java starter、不接 RocketMQ** 的接入方（画像 C：外购系统、遗留系统、非 Java 服务）。零第三方依赖、Node >= 18、纯 ESM，可直接把 `foundation-client.mjs` 复制进业务项目。

```
sdk-examples/node/
  foundation-client.mjs   参考实现（版本头 / 验签 / 幂等 / 降级标记）
  test/client.test.mjs    node:test 自检（18 例，含跨语言契约对齐）
  README.md              本文件
```

运行自检（无需 `npm install`）：

```bash
node --test sdk-examples/node/test/client.test.mjs
```

## 1. 接入形态

| 需求 | 通道 | 本示例提供的能力 |
| --- | --- | --- |
| 调 Foundation REST | HTTPS + OIDC | `buildApiHeaders` 带 `X-Foundation-Api-Version: 1`；`assertApiResponse` 把 400/406 与响应版本串路翻译成可读错误 |
| 订阅 `fdn.*` 事件 | Webhook（无需 MQ） | `verifyWebhook` 校验 HMAC-SHA256 签名、envelope、`schemaVersion`、契约头与头体一致性 |
| 处理中继重试 | 同上 | `createIdempotencyGuard` 按 `eventType + deliveryId` 去重 |
| 感知降级数据 | REST 响应头 | `isDegraded` 读 `X-Foundation-Degraded` |

完整契约见 [契约导览](../../docs/contracts/README.md)，服务端装配裁剪见 [服务端 profile](../../docs/adaptation/server-profiles.md)。

## 2. 最小用法

```js
import http from 'node:http';
import { verifyWebhook, createIdempotencyGuard, ContractError } from './foundation-client.mjs';

const SECRET = process.env.FOUNDATION_WEBHOOK_SECRET; // 勿写进代码或提交到仓库
const guard = createIdempotencyGuard();

http.createServer((req, res) => {
  // 关键：保留原始字节，不要先 JSON.parse 再传给 verifyWebhook
  const chunks = [];
  req.on('data', (c) => chunks.push(c));
  req.on('end', () => {
    try {
      const event = verifyWebhook(Buffer.concat(chunks), req.headers, SECRET);
      if (!guard.isNew(event)) {
        res.writeHead(200).end('duplicate');   // 重复投递必须先返回 2xx，否则中继会一直重试
        return;
      }
      if (!event.knownEventType) console.warn('未登记事件，请补契约：', event.eventType);
      handle(event);                            // 业务处理应可重入
      res.writeHead(204).end();
    } catch (err) {
      if (err instanceof ContractError) {
        res.writeHead(400).end(err.code);       // 验签失败不返回 2xx，避免丢事件
        return;
      }
      throw err;
    }
  });
}).listen(8080);
```

**验签必须用原始请求体字节**。Java 侧 `WebhookEventSink` 签的是 `objectMapper.writeValueAsBytes(body)` 的输出；任何 `JSON.parse` + `JSON.stringify` 的重序列化都会改变键顺序或空白，导致验签必然失败。`verifyWebhook` 会主动拒绝传入对象。

## 3. 契约对齐（防止跨语言漂移）

| 项 | 权威来源 | 本示例 |
| --- | --- | --- |
| 签名头 / 格式 / envelope 字段 / 事件注册表 | `foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json`（随 jar 发布） | `loadEventContract()` 运行时读取 |
| 签名算法与签名串 | `WebhookEventSink.java`：`sha256=<HMAC-SHA256(secret, raw_body)>` | `createHmac('sha256', ...)` + `timingSafeEqual` |
| API 版本头 / 主版本 | `FoundationApiVersion.java` | `API_VERSION_HEADER` / `CURRENT_API_MAJOR` |
| 降级响应头 | `DegradeHeaderFilter.java` | `DEGRADED_HEADER` |

`test/client.test.mjs` 会**直接读 Java 源码与 JSON 契约**逐项比对：Java 侧改了头名、签名算法、主版本或 envelope 字段而本示例没跟上，测试立刻失败，而不是靠文档注释提醒。

业务项目脱离本仓库使用时，把事件契约 JSON 一并随包发布，并通过 `verifyWebhook(..., { contractPath })` 指向它。

## 4. 刻意不做的事

1. **不发明时间戳防重放头**：v1 契约只定义 `X-Fdn-Event` / `X-Fdn-Schema-Version` / `X-Fdn-Delivery-Id` 与签名头。自行加 `X-Fdn-Timestamp` 并纳入签名会让 Java 侧签出来的串与接入方算的不一致。防重放如确有必要，应作为 v2 的**新增可选头**并保留并行发布期。
2. **不拒绝未登记事件**：Java 侧 `EventSchemas.versionOf` 对未登记事件回落 v1 属前向兼容；本示例同样放行但置 `knownEventType=false`，由接入方决定告警还是丢弃。
3. **不猜测 v2 结构**：`schemaVersion > 1` 直接抛 `UNSUPPORTED_SCHEMA_VERSION`，不按 v1 字段硬解析。
4. **幂等只做进程内**：`createIdempotencyGuard` 是单实例参考实现；多实例部署必须换成共享存储（Redis `SETNX` 或带唯一索引的表），否则重试会落在不同实例上。
5. **不做 OIDC 令牌获取**：令牌由接入方已有的身份库 / 网关处理，本示例只负责契约头与事件，不引入任何认证依赖。

## 5. CI

主 CI 的 `node-sdk` job 使用 Node 22 运行 `node --test sdk-examples/node/test/client.test.mjs`，与 Java 契约守门并列；任一契约文件变更导致不对齐都会在 PR 阶段变红。
