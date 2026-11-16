#!/usr/bin/env bash
# ADP-07 部署命名空间渲染：按 deployment-id 派生 realm / topic / 键前缀 / 桶 / schema，
# 生成对应的 Keycloak realm 导入文件，并输出可直接 eval 的 export 语句。
#
# 用法（在仓库根目录）：
#   eval "$(bash deploy/init/render-deployment.sh --id prod-a)"
#   docker compose -f deploy/compose.yml up -d --build
#
# 说明：
#   - id=default 时不生成新文件，所有派生值与改造前的字面量逐字一致（存量部署零改动）。
#   - 生成的 realm 文件为 foundation-realm.<id>.json，通过 FOUNDATION_REALM_FILE 挂进 compose。
#   - 服务端侧由 DeploymentNamespaceEnvironmentPostProcessor 用同一套规则派生，两边必须一致。

set -euo pipefail

ID="${FOUNDATION_DEPLOYMENT_ID:-default}"
while [ $# -gt 0 ]; do
  case "$1" in
    --id) ID="${2:-}"; shift 2 ;;
    --id=*) ID="${1#*=}"; shift ;;
    *) echo "用法：render-deployment.sh --id <deployment-id>" >&2; exit 2 ;;
  esac
done

if ! printf '%s' "$ID" | grep -Eq '^[a-z0-9]([a-z0-9-]{0,29}[a-z0-9])?$'; then
  echo "非法的 deployment-id：'${ID}'，需匹配 ^[a-z0-9]([a-z0-9-]{0,29}[a-z0-9])?$" >&2
  exit 2
fi

DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SRC_REALM="${DEPLOY_DIR}/init/foundation-realm.json"
REALM_FILE="${SRC_REALM}"

REALM="enterprise"
TOPIC="fdn-events"
KEY_PREFIX="fdn:"
BUCKET_PREFIX="fdn-"
BUCKET="fdn-app_foundation"
SCHEMA="foundation"
REDIS_PREFIX="fdn"

if [ "${ID}" != "default" ]; then
  REALM="enterprise-${ID}"
  TOPIC="fdn-events-${ID}"
  KEY_PREFIX="fdn:${ID}:"
  BUCKET_PREFIX="fdn-${ID}-"
  BUCKET="fdn-${ID}-app_foundation"
  SCHEMA="foundation-${ID}"
  REDIS_PREFIX="fdn-${ID}-"
  REALM_FILE="${DEPLOY_DIR}/init/foundation-realm.${ID}.json"

  # 只替换顶层 realm 字段；模板首行即 "realm": "enterprise"
  if ! grep -q '"realm"[[:space:]]*:[[:space:]]*"enterprise"' "${SRC_REALM}"; then
    echo "realm 模板结构变化，无法安全派生：${SRC_REALM}" >&2
    exit 3
  fi
  sed "0,/\"realm\"[[:space:]]*:[[:space:]]*\"enterprise\"/s//\"realm\": \"${REALM}\"/" \
      "${SRC_REALM}" > "${REALM_FILE}"
  echo "[render-deployment] 已生成 realm 导入文件：${REALM_FILE}" >&2
fi

SSO_URL="${FOUNDATION_SSO_PUBLIC_URL:-http://localhost:8180/realms/${REALM}}"

printf 'export FOUNDATION_DEPLOYMENT_ID=%q\n' "${ID}"
printf 'export FOUNDATION_KEYCLOAK_REALM=%q\n' "${REALM}"
printf 'export FOUNDATION_EVENT_TOPIC=%q\n' "${TOPIC}"
printf 'export FOUNDATION_CACHE_KEY_PREFIX=%q\n' "${KEY_PREFIX}"
printf 'export FOUNDATION_REDIS_PREFIX=%q\n' "${REDIS_PREFIX}"
printf 'export FOUNDATION_STORAGE_BUCKET=%q\n' "${BUCKET}"
printf 'export FOUNDATION_STORAGE_BUCKET_PREFIX=%q\n' "${BUCKET_PREFIX}"
printf 'export FOUNDATION_DB_SCHEMA=%q\n' "${SCHEMA}"
printf 'export FOUNDATION_SSO_PUBLIC_URL=%q\n' "${SSO_URL}"
printf 'export FOUNDATION_REALM_FILE=%q\n' "${REALM_FILE}"
