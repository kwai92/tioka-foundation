-- BAS-21 / F-11 §3.4：Foundation 库初始化（建库、账号、授权）
-- 单企业独立部署（ADR-029 第 60 条）
--
-- 由 docker-entrypoint-initdb.d 以超级用户执行；口令用 psql 变量注入，可用环境变量覆盖：
--   FDN_OWNER_PASSWORD（属主账号，执行迁移）/ FDN_APP_PASSWORD（运行账号，提供服务）
\set owner_pwd `echo "${FDN_OWNER_PASSWORD:-fdn_owner_dev}"`
\set app_pwd   `echo "${FDN_APP_PASSWORD:-fdn_app_dev}"`

-- 两个库：Foundation 库（权限快照、字典参数、审计）与 Keycloak 库（Keycloak 自身 schema）
CREATE DATABASE foundation;
CREATE DATABASE keycloak;

-- 账号分离（M4-06 §0.1）：属主账号执行 Flyway 迁移，运行账号提供服务；开发 / Compose 可只用一个账号
CREATE ROLE fdn_owner LOGIN PASSWORD :'owner_pwd';
CREATE ROLE fdn_app   LOGIN PASSWORD :'app_pwd';

GRANT ALL ON DATABASE foundation TO fdn_owner;
GRANT CONNECT ON DATABASE foundation TO fdn_app;

-- 运行账号 fdn_app 的权限说明（M4-06 §0.1）：
--   审计类表（audit_log、login_log 等）对 fdn_app 只授予 INSERT、SELECT；
--   其余业务表按需授予 INSERT / UPDATE / DELETE / SELECT；
--   迁移与 DDL 只由 fdn_owner 执行，运行账号不具备 DDL 权限。

-- PostgreSQL 15 起 public schema 默认不再对 PUBLIC 开放 CREATE；迁移在 public schema 建表，需授予属主
\connect foundation
ALTER SCHEMA public OWNER TO fdn_owner;
CREATE EXTENSION IF NOT EXISTS vector;

-- Keycloak 库由 Keycloak 自身 schema 管理（非 Flyway）；此处只建库与属主
\connect postgres
ALTER DATABASE keycloak OWNER TO fdn_owner;
