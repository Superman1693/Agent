-- 修复 E9/O12（二次修正）：vector_store 表维度与 embedding 模型对齐
--
-- 背景：
--   表原为 vector(1536)，而 text-embedding-v3 支持的维度上限是 1024
--   （允许值 [64,128,256,512,768,1024]），导致 PgVectorStore 初始化写入时
--   被 DashScope 拒绝：400 InvalidParameter - dimension for embedding is invalid。
--   表现为「应用启动直接失败」。
--
-- 前提：
--   执行前请确认 vector_store 为空（本次修复执行时 ROWS=0，无数据损失）。
--   如已有数据，请先备份，或改用「新增 1024 维列 + 重新向量化」的方式迁移。
--
-- 用法：
--   psql -h localhost -p 5432 -U postgres -d ai-agent -f pgvector_rebuild.sql

DROP TABLE IF EXISTS public.vector_store;

CREATE TABLE public.vector_store (
    id        uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    content   text  NOT NULL,
    metadata  jsonb,
    embedding vector(1024) NOT NULL
);

-- HNSW 索引可由 PgVectorStore 在写入后创建；如需手工创建：
-- CREATE INDEX IF NOT EXISTS vector_store_embedding_hnsw
--     ON public.vector_store USING hnsw (embedding vector_cosine_ops);
