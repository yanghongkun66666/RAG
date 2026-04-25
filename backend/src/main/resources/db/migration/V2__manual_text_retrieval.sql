CREATE TABLE IF NOT EXISTS manual_text_experiment (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255),
    raw_text TEXT NOT NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manual_text_chunk (
    id BIGSERIAL PRIMARY KEY,
    experiment_id BIGINT NOT NULL REFERENCES manual_text_experiment (id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    CONSTRAINT uk_manual_text_chunk_experiment_chunk_index UNIQUE (experiment_id, chunk_index),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_manual_text_chunk_experiment_id
    ON manual_text_chunk (experiment_id);
