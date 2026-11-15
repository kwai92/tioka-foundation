/**
 * ADP-09：C 类接入参考实现的自检。
 * 运行：node --test sdk-examples/node/test/   （零依赖，不需要 npm install）
 */
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { readFileSync } from 'node:fs';

import {
  API_VERSION_HEADER, CURRENT_API_MAJOR, DEGRADED_HEADER, SIGNATURE_HEADER,
  SIGNATURE_PREFIX, SUPPORTED_SCHEMA_VERSIONS, assertApiResponse, buildApiHeaders,
  createIdempotencyGuard, getHeader, isDegraded, loadEventContract, verifyWebhook,
} from '../foundation-client.mjs';

const REPO = new URL('../../../', import.meta.url);
const read = (relative) => readFileSync(new URL(relative, REPO), 'utf8');
const SECRET = 'it-secret';

const ENVELOPE = Object.freeze({
  schemaVersion: 1,
  eventType: 'fdn.dict.changed',
  aggregate: 'dt_1',
  deliveryId: 'evt_1',
  payload: { id: 'dt_1' },
});

/** 按 Java WebhookEventSink 的方式生成一次合法投递（返回原始字节 + 头）。 */
function signed(envelope = ENVELOPE, { secret = SECRET, headerOverrides = {}, omit = [] } = {}) {
  const raw = Buffer.from(JSON.stringify(envelope), 'utf8');
  const headers = {
    'Content-Type': 'application/json',
    'X-Fdn-Event': envelope.eventType,
    'X-Fdn-Schema-Version': String(envelope.schemaVersion),
    'X-Fdn-Delivery-Id': envelope.deliveryId,
    [SIGNATURE_HEADER]: SIGNATURE_PREFIX
      + createHmac('sha256', Buffer.from(secret, 'utf8')).update(raw).digest('hex'),
    ...headerOverrides,
  };
  for (const name of omit) delete headers[name];
  return { raw, headers };
}

// ---------------------------------------------------------------- 契约对齐

test('Node 侧常量与 Java / JSON 契约逐项对齐（防跨语言漂移）', () => {
  const contract = loadEventContract();
  const channel = contract.webhookChannel;
  assert.equal(channel.signatureHeader, SIGNATURE_HEADER);
  assert.match(channel.signatureFormat, /hmac_sha256\(secret,\s*raw_body\)/);
  assert.equal(channel.method, 'POST');
  assert.equal(channel.contentType, 'application/json');
  assert.deepEqual(channel.envelopeRequiredFields,
    ['schemaVersion', 'eventType', 'aggregate', 'deliveryId', 'payload']);
  assert.ok(channel.headers.includes('X-Fdn-Delivery-Id'));

  const apiVersionJava = read(
    'foundation-starter-core/src/main/java/com/foundation/starter/core/contract/FoundationApiVersion.java');
  assert.match(apiVersionJava, /HEADER\s*=\s*"([^"]+)"/);
  assert.equal(apiVersionJava.match(/HEADER\s*=\s*"([^"]+)"/)[1], API_VERSION_HEADER);
  assert.equal(Number(apiVersionJava.match(/CURRENT_MAJOR\s*=\s*(\d+)/)[1]), CURRENT_API_MAJOR);

  const sinkJava = read(
    'foundation-starter-mq/src/main/java/com/foundation/starter/mq/relay/WebhookEventSink.java');
  assert.match(sinkJava, /HmacSHA256/, '签名算法须与 Node 侧 createHmac(sha256) 一致');
  assert.match(sinkJava, /"sha256="\s*\+\s*sign\(envelope\.body\(\)\)/,
    '签名必须覆盖原始 envelope 字节，而不是重序列化结果');
  assert.match(sinkJava, /SIGNATURE_HEADER\s*=\s*"X-Fdn-Signature"/);

  const degradeJava = read(
    'foundation-starter-web/src/main/java/com/foundation/starter/web/filter/DegradeHeaderFilter.java');
  assert.match(degradeJava, new RegExp(DEGRADED_HEADER));
});

test('契约中所有已登记事件都能被验签解析', () => {
  const contract = loadEventContract();
  for (const event of contract.events) {
    const envelope = { ...ENVELOPE, eventType: event.eventType, schemaVersion: event.schemaVersion };
    const { raw, headers } = signed(envelope);
    assert.equal(verifyWebhook(raw, headers, SECRET).knownEventType, true, event.eventType);
  }
});

// ---------------------------------------------------------------- 验签

test('合法投递通过并返回 envelope', () => {
  const { raw, headers } = signed();
  assert.deepEqual(verifyWebhook(raw, headers, SECRET), { ...ENVELOPE, knownEventType: true });
});

test('请求体被改写后验签失败', () => {
  const { raw, headers } = signed();
  const tampered = Buffer.from(raw.toString('utf8').replace('dt_1', 'dt_2'), 'utf8');
  assert.throws(() => verifyWebhook(tampered, headers, SECRET),
    (e) => e.code === 'SIGNATURE_MISMATCH');
});

test('错误密钥验签失败', () => {
  const { raw, headers } = signed();
  assert.throws(() => verifyWebhook(raw, headers, 'wrong-secret'),
    (e) => e.code === 'SIGNATURE_MISMATCH');
});

test('签名头格式非法 / 缺失均被拒绝', () => {
  const { raw, headers } = signed();
  assert.throws(() => verifyWebhook(raw, { ...headers, [SIGNATURE_HEADER]: 'md5=abc' }, SECRET),
    (e) => e.code === 'SIGNATURE_MALFORMED');
  assert.throws(() => verifyWebhook(raw, { ...headers, [SIGNATURE_HEADER]: 'sha256=xyz' }, SECRET),
    (e) => e.code === 'SIGNATURE_MALFORMED');
  const without = { ...headers };
  delete without[SIGNATURE_HEADER];
  assert.throws(() => verifyWebhook(raw, without, SECRET), (e) => e.code === 'SIGNATURE_MISSING');
});

test('拒绝已解析对象，强制使用原始字节', () => {
  assert.throws(() => verifyWebhook(ENVELOPE, signed().headers, SECRET),
    (e) => e.code === 'RAW_BODY_REQUIRED');
  assert.throws(() => verifyWebhook(null, signed().headers, SECRET),
    (e) => e.code === 'RAW_BODY_REQUIRED');
});

test('缺少密钥 / 非法 JSON 均被拒绝', () => {
  const { raw, headers } = signed();
  assert.throws(() => verifyWebhook(raw, headers, ''), (e) => e.code === 'SECRET_REQUIRED');
  const body = Buffer.from('{not-json', 'utf8');
  const freshHeaders = { ...headers, [SIGNATURE_HEADER]: SIGNATURE_PREFIX
    + createHmac('sha256', SECRET).update(body).digest('hex') };
  assert.throws(() => verifyWebhook(body, freshHeaders, SECRET),
    (e) => e.code === 'ENVELOPE_INVALID');
});

// ---------------------------------------------------------------- envelope

test('envelope 必填字段逐个校验', () => {
  for (const field of ['schemaVersion', 'eventType', 'aggregate', 'deliveryId', 'payload']) {
    const broken = { ...ENVELOPE };
    delete broken[field];
    const { raw, headers } = signed(broken);
    assert.throws(() => verifyWebhook(raw, headers, SECRET),
      (e) => e.code === 'ENVELOPE_INVALID', field);
  }
  const badVersion = { ...ENVELOPE, schemaVersion: '1' };
  assert.throws(() => verifyWebhook(signed(badVersion).raw, signed(badVersion).headers, SECRET),
    (e) => e.code === 'ENVELOPE_INVALID');
});

test('未知 schemaVersion 明确拒绝，不按 v1 结构猜测', () => {
  const future = { ...ENVELOPE, schemaVersion: 2 };
  const { raw, headers } = signed(future);
  assert.throws(() => verifyWebhook(raw, headers, SECRET),
    (e) => e.code === 'UNSUPPORTED_SCHEMA_VERSION');
  assert.deepEqual(SUPPORTED_SCHEMA_VERSIONS, [1]);
});

test('契约头缺失 / 头体不一致均被拒绝', () => {
  const { raw, headers } = signed(ENVELOPE, { omit: ['X-Fdn-Delivery-Id'] });
  assert.throws(() => verifyWebhook(raw, headers, SECRET),
    (e) => e.code === 'HEADER_ENVELOPE_MISMATCH');

  const { raw: raw2, headers: headers2 } = signed(ENVELOPE, {
    headerOverrides: { 'X-Fdn-Event': 'fdn.org.changed' },
  });
  assert.throws(() => verifyWebhook(raw2, headers2, SECRET),
    (e) => e.code === 'HEADER_ENVELOPE_MISMATCH');
});

test('未登记事件按前向兼容放行但显式标记', () => {
  const unknown = { ...ENVELOPE, eventType: 'fdn.future.created' };
  const { raw, headers } = signed(unknown);
  const parsed = verifyWebhook(raw, headers, SECRET);
  assert.equal(parsed.knownEventType, false);
  assert.equal(parsed.eventType, 'fdn.future.created');
});

test('头名大小写不敏感（Node IncomingHttpHeaders 为小写）', () => {
  const { raw, headers } = signed();
  const lower = Object.fromEntries(Object.entries(headers).map(([k, v]) => [k.toLowerCase(), v]));
  assert.equal(getHeader(lower, SIGNATURE_HEADER), headers[SIGNATURE_HEADER]);
  assert.equal(verifyWebhook(raw, lower, SECRET).deliveryId, 'evt_1');
});

// ---------------------------------------------------------------- 幂等

test('同一 deliveryId 重试只处理一次，不同事件类型互不影响', () => {
  const guard = createIdempotencyGuard();
  const { raw, headers } = signed();
  const envelope = verifyWebhook(raw, headers, SECRET);
  assert.equal(guard.isNew(envelope), true);
  assert.equal(guard.isNew(envelope), false);
  assert.equal(guard.isNew({ ...envelope, deliveryId: 'evt_2' }), true);
  assert.equal(guard.isNew({ ...envelope, eventType: 'fdn.org.changed' }), true);
  assert.equal(guard.size(), 3);
});

test('过期后可再次处理（TTL 可注入，便于测试）', () => {
  let clock = 1000;
  const guard = createIdempotencyGuard({ ttlMs: 100, now: () => clock });
  const envelope = verifyWebhook(signed().raw, signed().headers, SECRET);
  assert.equal(guard.isNew(envelope), true);
  clock = 1099;
  assert.equal(guard.isNew(envelope), false);
  clock = 1101;
  assert.equal(guard.isNew(envelope), true);
  assert.equal(guard.size(), 1);
});

// ---------------------------------------------------------------- 版本头与降级

test('出站头显式携带当前主版本', () => {
  const headers = buildApiHeaders({ headers: { Accept: 'application/json' } });
  assert.equal(headers[API_VERSION_HEADER], String(CURRENT_API_MAJOR));
  assert.equal(headers.Accept, 'application/json');
  assert.throws(() => buildApiHeaders({ apiVersion: 0 }),
    (e) => e.code === 'API_VERSION_MALFORMED');
});

test('400 / 406 / 响应版本不一致分别给出可读错误码', () => {
  assert.throws(() => assertApiResponse({ status: 400, body: { code: 'FOUNDATION_API_VERSION_MALFORMED' } }),
    (e) => e.code === 'API_VERSION_MALFORMED');
  assert.throws(() => assertApiResponse({ status: 406, body: { code: 'FOUNDATION_API_VERSION_UNSUPPORTED' } }),
    (e) => e.code === 'UNSUPPORTED_API_VERSION');
  assert.throws(() => assertApiResponse({ status: 200, headers: { [API_VERSION_HEADER]: '2' } }),
    (e) => e.code === 'RESPONSE_VERSION_MISMATCH');
  assert.equal(assertApiResponse({ status: 200, headers: { [API_VERSION_HEADER]: 'v1' } }), 1);
  assert.equal(assertApiResponse({ status: 200, headers: {} }), undefined);
});

test('降级标记读取', () => {
  assert.equal(isDegraded({ [DEGRADED_HEADER]: 'true' }), true);
  assert.equal(isDegraded({ 'x-foundation-degraded': 'TRUE' }), true);
  assert.equal(isDegraded({}), false);
  assert.equal(isDegraded({ [DEGRADED_HEADER]: 'false' }), false);
});
