#!/usr/bin/env bash
# BAS-25 权限清单最小结构校验（无第三方依赖）。
#
# 注意：Foundation 侧最终由 com.foundation.authz.domain.ManifestParser 严格校验
#（固定形态 {"version":N,"menus":[...],"permissions":[...]}、编码正则、重复项等）；
# 本脚本只是 CI 前置守门，故只在 python3 或 node 可用时做等价的最小校验。
# 用法：check-permission-manifest.sh docs/权限清单.json
#
# 提示：模板原文含字面占位符，须先执行 rename.* 替换为新应用编码，本脚本才会通过。
set -euo pipefail

FILE="${1:-docs/权限清单.json}"
if [ ! -f "${FILE}" ]; then
  echo "权限清单文件不存在：${FILE}" >&2
  exit 1
fi

if command -v python3 >/dev/null 2>&1; then
  python3 - "${FILE}" <<'PY'
import json
import re
import sys

path = sys.argv[1]
perm_re = re.compile(r'^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$')
menu_re = re.compile(r'^[A-Za-z0-9][A-Za-z0-9_.:-]*$')

with open(path, encoding='utf-8') as fh:
    doc = json.load(fh)

if not isinstance(doc, dict):
    raise SystemExit('清单必须是 JSON 对象')
version = doc.get('version')
if not isinstance(version, int) or isinstance(version, bool) or version <= 0:
    raise SystemExit('version 必须为正整数')

menus = doc.get('menus', [])
perms = doc.get('permissions', [])
if not isinstance(menus, list) or not isinstance(perms, list):
    raise SystemExit('menus / permissions 必须是 JSON 数组')

menu_codes = set()
permission_codes = set()
for m in menus:
    code = m.get('code')
    if not isinstance(code, str) or not menu_re.match(code):
        raise SystemExit('非法菜单编码：%s' % code)
    if code in menu_codes:
        raise SystemExit('菜单编码重复：%s' % code)
    menu_codes.add(code)
for p in perms:
    code = p.get('code')
    if not isinstance(code, str) or not perm_re.match(code):
        raise SystemExit('非法权限码（应形如 资源:动作）：%s' % code)
    if code in permission_codes:
        raise SystemExit('权限码重复：%s' % code)
    permission_codes.add(code)
for m in menus:
    pc = m.get('permissionCode')
    if pc and pc not in permission_codes:
        raise SystemExit('菜单 %s 的 permissionCode 未在 permissions 中声明：%s' % (m.get('code'), pc))

print('权限清单结构校验通过：menus=%d permissions=%d' % (len(menus), len(perms)))
PY
  exit $?
fi

if command -v node >/dev/null 2>&1; then
  node - "${FILE}" <<'JS'
const fs = require('fs');
const path = process.argv[2];
const permRe = /^[a-z][a-z0-9_-]*(:[a-z0-9_-]+)+$/;
const menuRe = /^[A-Za-z0-9][A-Za-z0-9_.:-]*$/;
const doc = JSON.parse(fs.readFileSync(path, 'utf8'));
if (typeof doc !== 'object' || doc === null || Array.isArray(doc)) {
  throw new Error('清单必须是 JSON 对象');
}
if (!Number.isInteger(doc.version) || doc.version <= 0) {
  throw new Error('version 必须为正整数');
}
const menus = doc.menus || [];
const perms = doc.permissions || [];
const menuCodes = new Set();
const permCodes = new Set();
for (const m of menus) {
  if (typeof m.code !== 'string' || !menuRe.test(m.code)) throw new Error('非法菜单编码：' + m.code);
  if (menuCodes.has(m.code)) throw new Error('菜单编码重复：' + m.code);
  menuCodes.add(m.code);
}
for (const p of perms) {
  if (typeof p.code !== 'string' || !permRe.test(p.code)) throw new Error('非法权限码：' + p.code);
  if (permCodes.has(p.code)) throw new Error('权限码重复：' + p.code);
  permCodes.add(p.code);
}
for (const m of menus) {
  if (m.permissionCode && !permCodes.has(m.permissionCode)) {
    throw new Error('菜单 ' + m.code + ' 的 permissionCode 未在 permissions 中声明：' + m.permissionCode);
  }
}
console.log(`权限清单结构校验通过：menus=${menus.length} permissions=${perms.length}`);
JS
  exit $?
fi

echo "清单校验失败：未找到 python3 或 node。" >&2
echo "ADP-04：清单校验是 fail-closed 的，不允许静默跳过；请在 CI 镜像中安装 python3 或 node。" >&2
echo "（Foundation 侧 ManifestParser 的严格校验是第二道关口，不能替代 CI 前置守门。）" >&2
exit 1
