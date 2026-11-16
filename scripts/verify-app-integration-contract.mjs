#!/usr/bin/env node
// 平台接入契约守门（P0）：平台 schema 定义规则，派生应用实例与应用侧实现都不得漂移。
// 只读校验，不修改任何文件。运行：node scripts/verify-app-integration-contract.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const APP_ROOT = path.resolve(ROOT, '..');
const ok = [];
const fail = [];
const check = (cond, msg) => (cond ? ok : fail).push(msg);
const read = (rel) => fs.readFileSync(path.resolve(APP_ROOT, rel), 'utf8');
const exists = (rel) => fs.existsSync(path.resolve(APP_ROOT, rel));

const SCHEMA = 'tioka-foundation/docs/contracts/permission-code-mapping-v1.schema.json';
const INSTANCE = 'tioka-framework/src/main/resources/contracts/permission-code-mapping-v1.json';
const MAPPER = 'tioka-framework/src/main/java/com/tioka/framework/security/PermissionCodeMapper.java';
const PLATFORM_ID = 'urn:foundation:contract:permission-code-mapping:v1';
const POLICIES = {
  wildcardPolicy: 'deny-elevation',
  unknownCodePolicy: 'ignore',
  requiredCodePolicy: 'deny-unregistered',
};

check(exists(SCHEMA), '平台权限码映射 schema 存在');
check(exists(INSTANCE), '已知应用实例存在');
if (fail.length > 0) {
  console.log(`app-integration-contract: ${ok.length} passed, ${fail.length} failed`);
  for (const f of fail) console.error('  FAIL ' + f);
  process.exit(1);
}

const schema = JSON.parse(read(SCHEMA));
const instance = JSON.parse(read(INSTANCE));
const mapperJava = read(MAPPER);

// 1. 平台 schema 自身：字段与策略必须由平台冻结，且不得绑定第一个业务应用
check(schema.$id === PLATFORM_ID, 'schema 的平台契约标识正确');
check(schema.additionalProperties === false, 'schema 禁止应用自造字段');
check(schema.properties.version.const === 1, 'schema 冻结契约版本 v1');
for (const [key, value] of Object.entries(POLICIES)) {
  check(schema.properties[key].const === value, `schema 冻结策略 ${key}=${value}`);
  check(Array.isArray(schema.required) && schema.required.includes(key), `schema 必填项含 ${key}`);
}
check(schema.properties.applicationPermissionCodes !== undefined,
  'schema 使用应用无关的 applicationPermissionCodes');
check(schema.properties.tiokaPermissionCodes === undefined,
  'schema 不得绑定第一个业务应用名称');
check(schema.definitions.code.pattern.includes('*') === false,
  '权限码格式禁止通配项');
check(schema.properties.applicationPermissionCodes.minItems >= 1
  && schema.properties.applicationPermissionCodes.uniqueItems === true,
  '权限码目录必须非空且无重复');

// 2. 应用实例必须遵守平台 schema
check(instance.platformContract === PLATFORM_ID, '应用实例声明遵守平台契约');
check(instance.version === 1, '应用实例版本与平台一致');
for (const [key, value] of Object.entries(POLICIES)) {
  check(instance[key] === value, `应用实例未放宽策略 ${key}`);
}
for (const key of schema.required) {
  check(Object.prototype.hasOwnProperty.call(instance, key), `应用实例必填项齐全：${key}`);
}
const unknownFields = Object.keys(instance).filter((key) => schema.properties[key] === undefined);
check(unknownFields.length === 0, `应用实例不含平台未定义字段（发现：${unknownFields.join(', ') || '无'}）`);

const codePattern = new RegExp(schema.definitions.code.pattern);
const codes = instance.applicationPermissionCodes;
check(Array.isArray(codes) && codes.length > 0, '应用权限码目录非空');
check(new Set(codes).size === codes.length, '应用权限码目录无重复');
check(codes.every((code) => typeof code === 'string' && codePattern.test(code) && !code.includes('*')),
  '应用权限码均为具体项，格式合法且不含通配');
check(instance.superAdminCodes.every((code) => codePattern.test(code) && !code.includes('*')),
  '超管码不含通配项');
for (const [from, to] of Object.entries(instance.aliases)) {
  check(!from.includes('*') && codes.includes(to) && !to.includes('*'),
    `别名 ${from} -> ${to} 指向已登记的具体权限码`);
}

// 3. 应用侧实现不得私自改回旧字段或放宽策略
for (const [key, value] of Object.entries(POLICIES)) {
  check(mapperJava.includes(`"${value}"`), `应用映射器实现仍校验策略 ${key}`);
}
check(mapperJava.includes('root.path("applicationPermissionCodes")'),
  '应用映射器按平台字段名读取权限码目录');
check(!mapperJava.includes('tiokaPermissionCodes'),
  '应用映射器不再依赖绑定第一个业务应用的旧字段名');

// 4. 接入平面资产齐备（平台侧与骨架侧）
check(exists('tioka-foundation/foundation-starter-client/src/main/java/com/foundation/starter/client/integration/FoundationIntegrationSelfCheck.java'),
  '平台接入自检器存在');
check(exists('tioka-foundation/foundation-it-example/src/test/java/com/foundation/example/onboarding/SecondAppOnboardingTest.java'),
  '第二接入方骨架验收存在');
check(read('tioka-foundation/foundation-starter-client/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports')
  .includes('FoundationIntegrationAutoConfiguration'), '接入护栏已注册为平台自动装配');
check(!read('tioka-foundation/foundation-it-example/pom.xml').includes('<groupId>com.tioka</groupId>'),
  '第二接入方骨架不依赖第一个业务应用构件');

// 5. P1 运行平面：中台退出请求热路径、事件失效、三项契约缺口
const P = 'tioka-foundation/';
const CLIENT_AUTHZ = P + 'foundation-starter-client/src/main/java/com/foundation/starter/client/authz/';
const cacheJava = read(CLIENT_AUTHZ + 'AuthorizationSnapshotCache.java');
check(exists(CLIENT_AUTHZ + 'AuthorizationSnapshot.java')
  && exists(CLIENT_AUTHZ + 'CachingAuthorizationSnapshotClient.java')
  && exists(CLIENT_AUTHZ + 'AuthorizationSnapshotInvalidationListener.java'),
  '平台版本化缓存、装饰器与事件监听器齐备');
check(cacheJava.includes('fetched.version() < current.snapshot().version()'),
  '缓存拒绝版本回退，避免权限回退');
check(cacheJava.includes('return null') && cacheJava.includes('current.expired(now)')
  && !cacheJava.includes('allowStale'),
  '缓存过期后必须回源，回源失败一律返回 null，无过期放行分支');
check(cacheJava.includes('!personId.equals(fetched.personId())'),
  '回源结果按身份与应用校验，防串号');
check(read(CLIENT_AUTHZ + 'HttpAuthorizationSnapshotClient.java').includes('versionNode'),
  '轻量客户端保留版本号供缓存使用');
const clientAuto = read(P + 'foundation-starter-client/src/main/java/com/foundation/starter/client/config/FoundationClientAutoConfiguration.java');
check(clientAuto.includes('CachingAuthorizationSnapshotClient')
  && clientAuto.includes('SnapshotInvalidationHook')
  && clientAuto.includes('AuthorizationSnapshotInvalidationListener'),
  '接入模式默认自动装配缓存、失效钩子与事件入口，应用零代码');
check(clientAuto.includes('getClient().getAuthz()'),
  '轻量路径缓存配置收敛在 foundation.client.* 命名空间');

const eventContract = JSON.parse(read(P
  + 'foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json'));
check(eventContract.schemaVersion === 1, '事件契约主版本未升，v1 接入方零改动');
check(eventContract.mqChannel.reservedFields.includes('deliveryId'),
  'MQ body 声明 deliveryId 保留字段');
check(eventContract.webhookChannel.envelopeOptionalFields.includes('sentAt'),
  'Webhook envelope 声明可选 sentAt 防重放字段');
check(eventContract.webhookChannel.envelopeRequiredFields.length === 5,
  'v1 envelope 必需字段未增删');
check(read(P + 'foundation-starter-mq/src/main/java/com/foundation/starter/mq/outbox/MybatisOutbox.java')
  .includes('body.put("deliveryId", row.getId())'), 'deliveryId 与 outbox 行主键同源');
const sinkJava = read(P + 'foundation-starter-mq/src/main/java/com/foundation/starter/mq/relay/WebhookEventSink.java');
check(sinkJava.includes('body.put("sentAt", clock.millis())'),
  '防重放时间戳位于被 HMAC 覆盖的 body 内');
check(!sinkJava.includes('X-Fdn-Timestamp'), '不发明时间戳头破坏 v1 签名串');
check(exists(P + 'foundation-starter-mq/src/main/java/com/foundation/starter/mq/consumer/DeliveryAwareIdempotentConsumer.java')
  && read(P + 'foundation-starter-mq/src/main/java/com/foundation/starter/mq/consumer/EventDeliveryKeys.java')
    .includes('eventType + ":" + id'), '平台提供 eventType + deliveryId 幂等实现');
const degradeJava = read(P + 'foundation-starter-core/src/main/java/com/foundation/starter/core/context/DegradeContext.java');
for (const api of ['capture()', 'restore(Snapshot', 'wrap(Snapshot', 'call(Snapshot']) {
  check(degradeJava.includes(api), `DegradeContext 提供异步传递 API：${api}`);
}
check(read(P + 'foundation-starter-web/src/main/java/com/foundation/starter/web/filter/DegradeHeaderFilter.java')
  .includes('DegradeContext.capture()'), '过滤器绑定共享快照以接收异步回写');
const nodeSdk = read(P + 'sdk-examples/node/foundation-client.mjs');
check(nodeSdk.includes('assertReplayWindow'), 'Node SDK 提供时间窗防重放校验');
check(!nodeSdk.includes('X-Fdn-Timestamp'), 'Node SDK 不自行发明时间戳头');

console.log(`app-integration-contract: ${ok.length} passed, ${fail.length} failed`);
if (fail.length > 0) {
  for (const f of fail) console.error('  FAIL ' + f);
  process.exit(1);
}
console.log('OK: 平台定义规则、应用实例与实现三者一致');
