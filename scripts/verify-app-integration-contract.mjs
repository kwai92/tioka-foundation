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

console.log(`app-integration-contract: ${ok.length} passed, ${fail.length} failed`);
if (fail.length > 0) {
  for (const f of fail) console.error('  FAIL ' + f);
  process.exit(1);
}
console.log('OK: 平台定义规则、应用实例与实现三者一致');
