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
 * @param rawBody Buffer | string —— **必须是未解析的原始请求体字节**。
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
  if (typeof rawBody === 'object' && !Buffer.isBuffer(rawBody)) {
    fail('RAW_BODY_REQUIRED', '不要传入已解析的 JSON 对象，验签必须用原始字节');
  }
  const bytes = Buffer.isBuffer(rawBody) ? rawBody : Buffer.from(String(rawBody), 'utf8');
  if (typeof secret !== 'string' || secret.length === 0) {
    fail('SECRET_REQUIRED', '缺少 Webhook 签名密钥');
  }

  const contract = options.contract ?? loadEventContract(options.contractPath);
  const channel = contract.webhookChannel ?? {};
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
 * 契约**没有定义时间戳头**，因此本实现不自行发明防重放字段；需要防重放应作为 v2 新增可选头。
 * 进程内实现仅适用单实例；多实例请换成共享存储（Redis SETNX / 唯一索引）。
 */
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
