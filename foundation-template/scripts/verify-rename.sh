#!/usr/bin/env bash
# ADP-12：模板 {app} 重命名自检。
# 在临时目录复制模板 → rename.sh --app sample → 扫描占位符残留 → 权限清单校验。
# 不修改仓库内的模板本身；主 CI 与 docs/delivery/README.md §1 第 7 步调用。
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPO_ROOT="$(cd "${ROOT}/.." && pwd)"
cd "${REPO_ROOT}"

# 用相对路径的临时目录：Windows 下 mktemp -d 会返回 C:\... 盘符路径，
# 部分受管 shell 的删除保护会拒绝该格式，导致 trap 清理失败。
TMP="$(mktemp -d .verify-rename.XXXXXX)"
trap 'rm -rf "${TMP}"' EXIT

cp -a "${ROOT}/." "${TMP}/"
# 本脚本自身含动态构造的占位符，不应被改名脚本处理
rm -f "${TMP}/scripts/verify-rename.sh"

echo "== 1) 执行 rename.sh --app sample =="
bash "${TMP}/scripts/rename.sh" --app sample

# 用 printf 组装字面占位符，避免本脚本自身含有该串
TOKEN="$(printf '\173%s\175' app)"
leftover=0

echo "== 2) 扫描文件内容残留 =="
while IFS= read -r -d '' file; do
  if grep -qF -- "${TOKEN}" "${file}" 2>/dev/null; then
    echo "  [残留] 文件内容：${file#"${TMP}"/}"
    leftover=$((leftover + 1))
  fi
done < <(find "${TMP}" -type f -print0)

echo "== 3) 扫描文件名 / 目录名残留 =="
while IFS= read -r -d '' path; do
  case "$(basename "${path}")" in
    *"${TOKEN}"*)
      echo "  [残留] 路径：${path#"${TMP}"/}"
      leftover=$((leftover + 1))
      ;;
  esac
done < <(find "${TMP}" -print0)

if [ "${leftover}" -ne 0 ]; then
  echo "重命名自检失败：共 ${leftover} 处占位符残留" >&2
  exit 1
fi
echo "  [通过] 无占位符残留"

echo "== 4) 改名后权限清单仍可校验 =="
bash "${TMP}/scripts/check-permission-manifest.sh" "${TMP}/docs/权限清单.json"

echo "== 5) 改名后关键文件存在 =="
for f in pom.xml sample-server/pom.xml sample-example-api/pom.xml sample-example-biz/pom.xml \
         sample-ui/package.json .github/workflows/ci.yml; do
  if [ ! -f "${TMP}/${f}" ]; then
    echo "  [缺失] ${f}" >&2
    exit 1
  fi
done
echo "  [通过] 关键文件齐全"

echo "模板重命名自检：全部通过（临时目录已清理）"
