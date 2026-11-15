#!/usr/bin/env bash
# BAS-25 接入自检入口：S15 §12.1 六项 + 权限清单/构建/前端校验。
# 逐项打印检查指引与结果位；脚本项失败时以非零码退出。
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

fail=0
ok()   { printf '  [通过] %s\n' "$1"; }
todo() { printf '  [指引] %s\n' "$1"; }

echo "== 1) 权限清单格式校验（scripts/check-permission-manifest.sh） =="
if bash scripts/check-permission-manifest.sh docs/权限清单.json; then
  ok "docs/权限清单.json 结构合法"
else
  fail=1
fi

echo
echo "== 2) 后端构建与测试（mvn -B verify） =="
if command -v mvn >/dev/null 2>&1; then
  if mvn -B verify; then ok "mvn -B verify"; else fail=1; fi
else
  todo "未找到 mvn，跳过；请在具备 Maven 的环境执行 mvn -B verify"
fi

echo
echo "== 3) 前端构建（npm ci && npm run build） =="
if command -v npm >/dev/null 2>&1 && [ -f "{app}-ui/package.json" ]; then
  if ( cd "{app}-ui" && npm ci && npm run build ); then ok "前端 build"; else fail=1; fi
else
  todo "未找到 npm 或前端目录，跳过前端构建（可忽略）"
fi

echo
echo "== S15 §12.1 六项接入自检（在管理台自检页 / 浏览器逐项确认） =="
echo "  1. 登录     ：检查 门户跳转登录、令牌经 starter 校验       → 通过标准：进入首页且请求不再 401"
echo "  2. 菜单     ：检查 授权快照菜单与页面路由一致              → 通过标准：只出现已授权菜单，深链可直达"
echo "  3. 按钮     ：检查 按钮权限码与接口 @RequirePermission 一致 → 通过标准：无权按钮隐藏，直调接口 403"
echo "  4. 数据范围 ：检查 列表按快照数据范围过滤                  → 通过标准：超出范围的数据不可见、不可改"
echo "  5. 单点注销 ：检查 门户注销后本应用会话失效                → 通过标准：刷新或再次请求回到登录"
echo "  6. 离职联动 ：检查 人员离职后账号停用                      → 通过标准：会话失效、待办按移交规则转移"
echo
if [ "${fail}" -eq 0 ]; then
  echo "自检脚本项：全部通过"
else
  echo "自检脚本项：存在失败，请查看上方输出"
fi
exit "${fail}"
