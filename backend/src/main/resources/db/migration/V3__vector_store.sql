CREATE TABLE IF NOT EXISTS public.vector_store (
    embedding_id UUID PRIMARY KEY,
    embedding vector(1024),
    text TEXT NULL,
    metadata JSONB NULL
);

CREATE INDEX IF NOT EXISTS idx_vector_store_embedding_cosine
    ON public.vector_store
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX IF NOT EXISTS idx_vector_store_experiment_id
    ON public.vector_store ((metadata->>'experimentId'));
