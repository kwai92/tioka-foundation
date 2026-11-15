#!/usr/bin/env bash
# BAS-25 模板改名：把目标工程（本脚本所在目录的父目录）内的目录名、文件名、文件内容中的
# 字面占位符替换为新应用编码；跳过 target/、node_modules/、dist/、.git/。幂等，并打印替换文件数。
set -euo pipefail

APP=""
while [ $# -gt 0 ]; do
  case "$1" in
    --app) APP="${2:-}"; shift 2 ;;
    --app=*) APP="${1#*=}"; shift ;;
    *) echo "用法：rename.sh --app <新应用编码>" >&2; exit 2 ;;
  esac
done

if [ -z "${APP}" ]; then
  echo "用法：rename.sh --app <新应用编码>" >&2
  exit 2
fi

if ! printf '%s' "${APP}" | grep -Eq '^[a-z][a-z0-9-]*$'; then
  echo "应用编码需匹配 ^[a-z][a-z0-9-]*$（如 contract）：${APP}" >&2
  exit 2
fi

# 用 printf 组装字面占位符，使本脚本自身不含该字面串，从而被扫描时保持原样。
TOKEN="$(printf '\173%s\175' app)"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

PRUNE=( -name target -o -name node_modules -o -name dist -o -name .git )

# 1) 文件内容替换
replaced=0
while IFS= read -r -d '' file; do
  if grep -qF -- "${TOKEN}" "${file}" 2>/dev/null; then
    tmp="$(mktemp)"
    sed "s/${TOKEN}/${APP}/g" "${file}" > "${tmp}"
    mv "${tmp}" "${file}"
    replaced=$((replaced + 1))
  fi
done < <(find "${ROOT}" \( "${PRUNE[@]}" \) -prune -o -type f -print0)

# 2) 文件名替换
while IFS= read -r -d '' path; do
  dir="$(dirname "${path}")"
  base="$(basename "${path}")"
  newbase="${base//${TOKEN}/${APP}}"
  if [ "${base}" != "${newbase}" ]; then
    mv "${path}" "${dir}/${newbase}"
  fi
done < <(find "${ROOT}" \( "${PRUNE[@]}" \) -prune -o -type f -name "*${TOKEN}*" -print0)

# 3) 目录名替换（自底向上：先深后浅）
while IFS= read -r -d '' dir; do
  parent="$(dirname "${dir}")"
  base="$(basename "${dir}")"
  newbase="${base//${TOKEN}/${APP}}"
  if [ "${base}" != "${newbase}" ]; then
    mv "${dir}" "${parent}/${newbase}"
  fi
done < <(find "${ROOT}" \( "${PRUNE[@]}" \) -prune -o -type d -name "*${TOKEN}*" -print0 | sort -rz)

echo "已替换文件内容：${replaced} 个；新应用编码：${APP}"
