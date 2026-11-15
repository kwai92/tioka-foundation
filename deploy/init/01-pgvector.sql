-- BAS-21 / F-11 §3.4：Foundation 库镜像启用 pgvector 扩展
-- 由 docker-entrypoint-initdb.d 在实例首次初始化时执行（POSTGRES_DB 库内）。
CREATE EXTENSION IF NOT EXISTS vector;
