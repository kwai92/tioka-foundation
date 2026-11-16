/**
 * Foundation C 类接入参考实现（ADP-09）：零第三方依赖，Node >= 18，纯 ESM。
 *
 * 面向「不引 Java starter、不走 RocketMQ」的接入方：OIDC + REST 版本头 + Webhook 事件。
 * 本文件是**可复制的参考实现**，不是 npm 包；接入方可整文件拷贝进自己的服务。
 *
 * 契约来源（唯一权威，勿在本文件重复定义）：
 *   - ../../foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json
 *   - ../../foundation-starter-core/src/main/java/com/foundation/starter/core/contract/FoundationApiVersion.java
 * 常量对齐由 test/client.test.mjs 逐项断言，Java 侧改契约而这里没跟上会直接测试失败。
 */

import { createHmac, timingSafeEqual } from 'node:crypto';
import { readFileSync } from 'node:fs';

/** 仓库根（本文件位于 sdk-examples/node/）。 */
const REPO_ROOT = new URL('../../', import.meta.url);

/** 事件契约路径；发布到业务项目时可通过 options.contractPath 覆盖为随包附带的一份。 */
export const DEFAULT_CONTRACT_PATH = new URL(
  'foundation-starter-mq/src/main/resources/contracts/event-schemas-v1.json', REPO_ROOT);

// 与 FoundationApiVersion.java 对齐（测试会读 Java 源码核对）
export const API_VERSION_HEADER = 'X-Foundation-Api-Version';
export const CURRENT_API_MAJOR = 1;

// 与 DegradeHeaderFilter.java 对齐
export const DEGRADED_HEADER = 'X-Foundation-Degraded';

// 与 WebhookEventSink.java / event-schemas-v1.json 对齐
export const SIGNATURE_HEADER = 'X-Fdn-Signature';
export const SIGNATURE_PREFIX = 'sha256=';
export const SUPPORTED_SCHEMA_VERSIONS = Object.freeze([1]);

/** 本参考实现能解析的 REST 主版本；与 assertApiResponse 的缺省 requestedMajor 必须同源。 */
export const SUPPORTED_API_MAJORS = Object.freeze([CURRENT_API_MAJOR]);

/** 带业务错误码的契约异常，便于接入方映射成自己的 4xx / 告警。 */
export class ContractError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ContractError';
    this.code = code;
  }
}

const fail = (code, message) => {
  throw new ContractError(code, message);
};

/** 原始字节的合法形态：Buffer / ArrayBufferView（含 Uint8Array）/ ArrayBuffer。 */
const isBinary = (value) => Buffer.isBuffer(value)
  || ArrayBuffer.isView(value)
  || value instanceof ArrayBuffer;

/** 已解析的 JSON 对象不得用于验签；二进制形态一律接受，避免 fetch/Workers 接入方被迫转换。 */
const isParsedObject = (value) => value !== null
  && typeof value === 'object' && !isBinary(value);

/** 无损转成 Buffer，不重序列化、不改变字节。 */
const toRawBytes = (value) => {
  if (Buffer.isBuffer(value)) return value;
  if (typeof value === 'string') return Buffer.from(value, 'utf8');
  if (value instanceof ArrayBuffer) return Buffer.from(value);
  return Buffer.from(value.buffer, value.byteOffset, value.byteLength);
};

/** 读取随 jar 发布的事件契约；默认指向仓库内权威文件。 */
export function loadEventContract(contractPath = DEFAULT_CONTRACT_PATH) {
  return JSON.parse(readFileSync(contractPath, 'utf8'));
}

/** 大小写不敏感取头；兼容 Node IncomingHttpHeaders、fetch Headers 与普通对象。 */
export function getHeader(headers, name) {
  if (!headers) return undefined;
  if (typeof headers.get === 'function') return headers.get(name) ?? undefined;
  const wanted = name.toLowerCase();
  for (const [key, value] of Object.entries(headers)) {
    if (key.toLowerCase() === wanted) return Array.isArray(value) ? value[0] : value;
  }
  return undefined;
}

/**
 * 构造出站 REST 请求头。缺省不带版本头也是合法的（服务端把缺省视为 v1），
 * 但显式带上能让网关并行期路由有据可依，推荐始终携带。
 */
export function buildApiHeaders({ apiVersion = CURRENT_API_MAJOR, headers = {} } = {}) {
  if (!Number.isInteger(apiVersion) || apiVersion < 1) {
    fail('API_VERSION_MALFORMED', 'apiVersion 必须是正整数，当前为 ' + apiVersion);
  }
  if (!SUPPORTED_API_MAJORS.includes(apiVersion)) {
    fail('UNSUPPORTED_API_VERSION',
      '本 SDK 仅支持 REST 主版本 ' + SUPPORTED_API_MAJORS.join(',') + '，当前请求 ' + apiVersion);
  }
  return { ...headers, [API_VERSION_HEADER]: String(apiVersion) };
}

/**
 * 校验 REST 响应：把服务端的 400 / 406 版本错误翻译成可读异常，
 * 并检出「响应主版本与请求不一致」（网关路由串版本）。
 */
export function assertApiResponse({ status, headers, body } = {}, requestedMajor = CURRENT_API_MAJOR) {
  if (status === 400) {
    fail('API_VERSION_MALFORMED', '请求版本头格式非法：' + describeBody(body));
  }
  if (status === 406) {
    fail('UNSUPPORTED_API_VERSION', '服务端不支持所请求的主版本：' + describeBody(body));
  }
  if (status !== undefined && (!Number.isInteger(status) || status < 200 || status >= 300)) {
    fail('HTTP_ERROR', 'HTTP 状态 ' + status + ' 不是成功响应，不能按成功结果解析');
  }
  const echoed = getHeader(headers, API_VERSION_HEADER);
  if (echoed === undefined || echoed === null || echoed === '') return undefined;
  const raw = String(echoed).trim();
  const digits = /^[vV]/.test(raw) ? raw.slice(1) : raw;
  if (!/^\d+$/.test(digits)) {
    fail('API_VERSION_MALFORMED', '响应版本头格式非法：' + raw);
  }
  const major = Number(digits);
  if (major !== requestedMajor) {
    fail('RESPONSE_VERSION_MISMATCH',
      `响应主版本 ${major} 与请求 ${requestedMajor} 不一致，检查网关版本路由`);
  }
  return major;
}

const describeBody = (body) => {
  if (body === undefined || body === null) return '(无响应体)';
  return typeof body === 'string' ? body : JSON.stringify(body);
};

/** 读取降级标记：命中过期缓存时 Foundation 会回写该头，业务侧可提示「数据可能不是最新」。 */
export function isDegraded(headers) {
  return String(getHeader(headers, DEGRADED_HEADER) ?? '').toLowerCase() === 'true';
}

/**
 * 校验并解析 Webhook 投递。
 *
 * @param rawBody Buffer | Uint8Array | ArrayBuffer | string —— **必须是未解析的原始请求体字节**。
 *   签名覆盖的是 Java 侧 objectMapper.writeValueAsBytes 的输出；任何 JSON.parse +
 *   JSON.stringify 的重序列化都会改键顺序 / 空格，导致验签必然失败。
 * @param headers 原始请求头
 * @param secret  与 foundation.mq.webhook.secret 相同的 UTF-8 密钥
 * @param options { contract, contractPath, supportedSchemaVersions }
 * @returns 校验通过的 envelope（含 knownEventType 标记）
 */
export function verifyWebhook(rawBody, headers, secret, options = {}) {
  if (rawBody === undefined || rawBody === null) {
    fail('RAW_BODY_REQUIRED', 'Webhook 验签必须使用原始请求体字节');
  }
  if (isParsedObject(rawBody)) {
    fail('RAW_BODY_REQUIRED', '不要传入已解析的 JSON 对象，验签必须用原始字节');
  }
  const bytes = toRawBytes(rawBody);
  if (typeof secret !== 'string' || secret.trim().length === 0) {
    fail('SECRET_REQUIRED', '缺少 Webhook 签名密钥');
  }

  const contract = options.contract ?? loadEventContract(options.contractPath);
  if (!contract || typeof contract !== 'object' || !contract.webhookChannel) {
    fail('CONTRACT_INVALID', '事件契约缺少 webhookChannel，检查 options.contractPath 是否指向随包契约');
  }
  const channel = contract.webhookChannel;
  if (channel.signatureHeader && channel.signatureHeader !== SIGNATURE_HEADER) {
    fail('CONTRACT_DRIFT', '契约签名头已变更：' + channel.signatureHeader);
  }
  if (channel.signatureFormat && !channel.signatureFormat.includes('raw_body')) {
    fail('CONTRACT_DRIFT', '契约签名串已变更：' + channel.signatureFormat);
  }

  const signature = getHeader(headers, SIGNATURE_HEADER);
  if (!signature) {
    fail('SIGNATURE_MISSING', '缺少 ' + SIGNATURE_HEADER + ' 头');
  }
  const provided = String(signature);
  if (!provided.startsWith(SIGNATURE_PREFIX)) {
    fail('SIGNATURE_MALFORMED', '签名格式应为 ' + SIGNATURE_PREFIX + '<64 位十六进制>');
  }
  const hex = provided.slice(SIGNATURE_PREFIX.length);
  if (!/^[0-9a-fA-F]{64}$/.test(hex)) {
    fail('SIGNATURE_MALFORMED', '签名不是 64 位十六进制 HMAC-SHA256');
  }
  const expected = createHmac('sha256', Buffer.from(secret, 'utf8')).update(bytes).digest();
  const actual = Buffer.from(hex, 'hex');
  if (actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
    fail('SIGNATURE_MISMATCH', 'Webhook 签名不匹配（密钥错误或请求体被改写）');
  }

  let envelope;
  try {
    envelope = JSON.parse(bytes.toString('utf8'));
  } catch {
    fail('ENVELOPE_INVALID', '请求体不是合法 JSON');
  }
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) {
    fail('ENVELOPE_INVALID', 'envelope 必须是 JSON 对象');
  }
  for (const field of channel.envelopeRequiredFields ?? [
    'schemaVersion', 'eventType', 'aggregate', 'deliveryId', 'payload']) {
    if (!(field in envelope)) {
      fail('ENVELOPE_INVALID', 'envelope 缺字段：' + field);
    }
  }
  if (!Number.isInteger(envelope.schemaVersion) || envelope.schemaVersion < 1) {
    fail('ENVELOPE_INVALID', 'schemaVersion 必须是正整数');
  }
  for (const field of ['eventType', 'aggregate', 'deliveryId']) {
    if (typeof envelope[field] !== 'string' || envelope[field].length === 0) {
      fail('ENVELOPE_INVALID', field + ' 必须是非空字符串');
    }
  }
  if (!envelope.payload || typeof envelope.payload !== 'object' || Array.isArray(envelope.payload)) {
    fail('ENVELOPE_INVALID', 'payload 必须是对象');
  }

  const supported = options.supportedSchemaVersions ?? SUPPORTED_SCHEMA_VERSIONS;
  if (!supported.includes(envelope.schemaVersion)) {
    fail('UNSUPPORTED_SCHEMA_VERSION',
      `尚不支持 schemaVersion=${envelope.schemaVersion}；请等待对应契约文件与并行发布期，不要按 v1 结构解析`);
  }

  // 头与 envelope 一致性：中继可能重试，头体不一致说明中间被改写或伪造
  const expectedPairs = [
    ['X-Fdn-Event', envelope.eventType],
    ['X-Fdn-Schema-Version', String(envelope.schemaVersion)],
    ['X-Fdn-Delivery-Id', envelope.deliveryId],
  ];
  for (const [name, value] of expectedPairs) {
    const actualHeader = getHeader(headers, name);
    if (actualHeader !== undefined && String(actualHeader) !== value) {
      fail('HEADER_ENVELOPE_MISMATCH', `${name}=${actualHeader} 与 envelope 的 ${value} 不一致`);
    }
  }
  for (const name of channel.headers ?? []) {
    if (getHeader(headers, name) === undefined) {
      fail('HEADER_ENVELOPE_MISMATCH', '缺少契约头：' + name);
    }
  }

  const registered = new Set((contract.events ?? []).map((e) => e.eventType));
  return {
    schemaVersion: envelope.schemaVersion,
    eventType: envelope.eventType,
    aggregate: envelope.aggregate,
    deliveryId: envelope.deliveryId,
    payload: envelope.payload,
    // 未登记事件按 Java 侧 EventSchemas.versionOf 的前向兼容语义放行，但显式标记，
    // 接入方应据此告警补登记，而不是默默当成已知业务处理。
    knownEventType: registered.has(envelope.eventType),
  };
}

/**
 * 按 eventType + deliveryId 幂等去重（契约要求；中继失败会整条重试，同一 deliveryId 可能多次到达）。
 * 防重放的时间窗由 assertReplayWindow 基于「已签名 body 内的 sentAt」完成，本守卫只负责去重。
 * 契约仍不定义时间戳头，签名串也仍是 raw_body，v1 接入方零改动。
 * 进程内实现仅适用单实例；多实例请换成共享存储（Redis SETNX / 唯一索引）。
 */
/**
 * 校验投递时间窗（P1 防重放，可选启用）。
 *
 * 必须在 verifyWebhook 之后调用，且传入「用已验签的原始字节解析出的 envelope」：
 * sentAt 位于 HMAC 覆盖的 body 内，任何改写都会让签名失效，因此可以作为可信时间源。
 * 平台刻意不新增时间戳头——那会改变跨语言签名串并破坏 v1 接入方。
 *
 * v1 老中继没有 sentAt 时返回 { enforced: false }，不抛错，保留并行发布期兼容；
 * 接入方应据此告警升级中继。时间窗校验 + deliveryId 幂等共同构成完整防重放。
 *
 * @param envelope 已验签的原始 envelope 对象（不是 verifyWebhook 的返回值）
 * @param options  { maxAgeMs, skewMs, now }
 */
export function assertReplayWindow(envelope, {
  maxAgeMs = 5 * 60 * 1000,
  skewMs = 30 * 1000,
  now = () => Date.now(),
} = {}) {
  const sentAt = envelope?.sentAt;
  if (sentAt === undefined) {
    return { enforced: false, reason: 'V1_ENVELOPE_WITHOUT_SENT_AT' };
  }
  if (!Number.isSafeInteger(sentAt) || sentAt <= 0) {
    fail('REPLAY_WINDOW_INVALID', 'sentAt 必须是正整数毫秒时间戳');
  }
  if (!Number.isFinite(maxAgeMs) || maxAgeMs <= 0 || !Number.isFinite(skewMs) || skewMs < 0) {
    fail('REPLAY_WINDOW_INVALID', 'maxAgeMs 必须为正数、skewMs 必须非负');
  }
  const current = now();
  if (sentAt > current + skewMs || current - sentAt > maxAgeMs) {
    fail('REPLAY_WINDOW_EXPIRED',
      `投递时间超出允许窗口：sentAt=${sentAt}, now=${current}, maxAgeMs=${maxAgeMs}`);
  }
  return { enforced: true };
}

export function createIdempotencyGuard({ ttlMs = 24 * 60 * 60 * 1000, now = () => Date.now() } = {}) {
  const seen = new Map();
  return {
    /** 首次见到返回 true 并记账；重复投递返回 false。验签通过后才调用。 */
    isNew(envelope) {
      const key = envelope.eventType + '\u0000' + envelope.deliveryId;
      const current = now();
      for (const [k, expiresAt] of seen) {
        if (expiresAt <= current) seen.delete(k);
      }
      if (seen.has(key)) return false;
      seen.set(key, current + ttlMs);
      return true;
    },
    size() {
      return seen.size;
    },
  };
}
