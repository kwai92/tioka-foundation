#!/usr/bin/env node
/**
 * ADP-11：兼容矩阵的一致性守门（零依赖，Node >= 18）。
 *
 * 把 docs/contracts/compatibility-matrix-v1.json 与「真实代码 / 真实 CI」逐项比对，
 * 防止文档声称支持某组合、代码里却已经改掉，或 CI 矩阵悄悄少跑一档。
 *
 *   node scripts/verify-compatibility-matrix.mjs
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = fileURLToPath(new URL('../', import.meta.url));
const read = (p) => readFileSync(join(ROOT, p), 'utf8');
const exists = (p) => existsSync(join(ROOT, p));
const errors = [];
const check = (ok, message) => { if (!ok) errors.push(message); };
const sameSet = (a, b) => a.length === b.length && [...a].sort().join('|') === [...b].sort().join('|');

const contractPath = 'docs/contracts/compatibility-matrix-v1.json';
check(exists(contractPath), '缺少兼容矩阵契约：' + contractPath);
if (!exists(contractPath)) {
  console.error(errors.join('\n'));
  process.exit(1);
}
const matrix = JSON.parse(read(contractPath));

// ---------------------------------------------------------------- 构建基线
const parentPom = read('foundation-parent/pom.xml');
const javaVersion = parentPom.match(/<java\.version>(\d+)<\/java\.version>/)?.[1];
const compilerRelease = parentPom.match(/<maven\.compiler\.release>(\d+)<\/maven\.compiler\.release>/)?.[1];
const bootVersion = parentPom.match(/<spring-boot\.version>([^<]+)<\/spring-boot\.version>/)?.[1];
check(javaVersion === matrix.server.java[0], `server.java=${matrix.server.java} 与 java.version=${javaVersion} 不一致`);
check(compilerRelease === javaVersion, 'java.version 与 maven.compiler.release 不一致');
check(bootVersion === matrix.server.springBoot,
  `server.springBoot=${matrix.server.springBoot} 与实际 ${bootVersion} 不一致`);

// ---------------------------------------------------------------- 运行模式
const modeJava = read('foundation-starter-core/src/main/java/com/foundation/starter/core/runtime/FoundationRuntimeMode.java');
const modeCases = [...modeJava.matchAll(/case\s+"([^"]+)"(?:\s*,\s*"[^"]+")?\s*->/g)].map((m) => m[1]);
const modeConstants = [...modeJava.matchAll(/^\s+([A-Z][A-Z_]*)[,;]/gm)].map((m) => m[1]);
check(sameSet(modeCases, matrix.runtimeModes),
  `runtimeModes 与 FoundationRuntimeMode.parse 分支不一致：契约 ${matrix.runtimeModes} / 代码 ${modeCases}`);
check(modeConstants.length === matrix.runtimeModes.length,
  `FoundationRuntimeMode 枚举常量 ${modeConstants.length} 个，契约声明 ${matrix.runtimeModes.length} 个`);

// ---------------------------------------------------------------- 服务端 profile
const profileJava = read('foundation-server/src/main/java/com/foundation/server/profile/FoundationServerProfile.java');
const profileKeys = [...profileJava.matchAll(/^\s+([A-Z_]+)\("([a-z]+)"\)[,;]/gm)].map((m) => m[2]);
check(sameSet(profileKeys, matrix.serverProfiles),
  `serverProfiles 与 FoundationServerProfile 构造值不一致：契约 ${matrix.serverProfiles} / 代码 ${profileKeys}`);

const baselinePath = 'foundation-server/src/test/resources/contracts/server-profile-domains-v1.txt';
const baseline = Object.fromEntries(read(baselinePath).trim().split('\n').map((l) => l.split('=')));
check(sameSet(Object.keys(baseline), matrix.serverProfiles),
  '装配 profile 冻结基线与矩阵契约的 profile 集合不一致');
for (const [profile, domains] of Object.entries(baseline)) {
  check(domains.split(',').includes('common'),
    `CM-04 违规：${profile} profile 未包含 common 前置域`);
}

// ---------------------------------------------------------------- 能力开关
function javaSources(dir, out = []) {
  for (const entry of readdirSync(join(ROOT, dir), { withFileTypes: true })) {
    if (['target', '.git', 'node_modules', '.workbuddy'].includes(entry.name)) continue;
    const rel = join(dir, entry.name);
    if (entry.isDirectory()) javaSources(rel, out);
    else if (entry.name.endsWith('.java')) out.push(rel);
  }
  return out;
}
const mainSources = javaSources('foundation-starter-core')
  .concat(javaSources('foundation-starter-audit'), javaSources('foundation-starter-cache'),
    javaSources('foundation-starter-client'), javaSources('foundation-starter-data'),
    javaSources('foundation-starter-mq'), javaSources('foundation-starter-observability'),
    javaSources('foundation-starter-security'), javaSources('foundation-starter-web'))
  .filter((p) => p.includes('src' + '\\main\\') || p.includes('src/main/') || p.includes('src/main\\')
    || p.replace(/\\/g, '/').includes('/src/main/'));
const foundCapabilities = new Set();
for (const file of mainSources) {
  for (const m of read(file).matchAll(/@ConditionalOnFoundationCapability\("([^"]+)"\)/g)) {
    foundCapabilities.add(m[1]);
  }
}
check(sameSet([...foundCapabilities], matrix.capabilities),
  `capabilities 与 @ConditionalOnFoundationCapability 实际取值不一致：契约 ${matrix.capabilities} / 代码 ${[...foundCapabilities]}`);

// ---------------------------------------------------------------- 锁定技术栈
const compose = read('deploy/compose.yml');
for (const image of matrix.server.lockedStackImages) {
  check(compose.includes(image), `deploy/compose.yml 缺少锁定栈镜像 ${image}`);
}
check(matrix.server.notAdapted.length >= 3, 'CM-05：notAdapted 清单不得为空，适配边界必须显式声明');

// ---------------------------------------------------------------- CI 矩阵
const ci = read('.github/workflows/ci.yml');
const axes = matrix.ciMatrix.axes;
check(ci.includes(`mode: [${axes.mode.join(', ')}]`), 'CI 矩阵 mode 轴与契约不一致');
check(ci.includes(`profile: [${axes.profile.join(', ')}]`), 'CI 矩阵 profile 轴与契约不一致');
check(ci.includes('FOUNDATION_COMPAT_MODE') && ci.includes('FOUNDATION_COMPAT_PROFILE'),
  'CI 未把组合注入探针环境变量，矩阵会退化成空跑');
check(ci.includes('node --test sdk-examples/node/test/client.test.mjs'),
  'CI 未运行 C 类 Node 接入包自检');

const cartesian = axes.mode.flatMap((mode) => axes.profile.map((profile) => `${mode}/${profile}`));
check(sameSet(cartesian, matrix.ciMatrix.combinations),
  `ciMatrix.combinations 不是两轴笛卡尔积：契约 ${matrix.ciMatrix.combinations} / 应为 ${cartesian}`);
check(matrix.server.javaPlannedUnverified.every((v) => !ci.includes(`java-version: '${v}'`)),
  'CM-06 违规：未经验证的 JDK 版本不得进入 CI 矩阵');

// ---------------------------------------------------------------- 输出
if (errors.length > 0) {
  console.error('兼容矩阵一致性校验失败：\n - ' + errors.join('\n - '));
  process.exit(1);
}
console.log([
  '兼容矩阵一致性校验通过',
  `  JDK ${javaVersion} / Spring Boot ${bootVersion} / 锁定栈 ${matrix.server.lockedStack.length} 项`,
  `  运行模式 ${matrix.runtimeModes.length} · 装配 profile ${matrix.serverProfiles.length} · 能力开关 ${matrix.capabilities.length}`,
  `  CI 组合 ${matrix.ciMatrix.combinations.length}（${cartesian.join(', ')}）`,
  `  不适配声明 ${matrix.server.notAdapted.length} 条`,
].join('\n'));
