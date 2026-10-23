-- Table used by Spring AI's PgVectorStore. Flyway owns the schema, so Spring AI's own
-- schema initialisation is switched off (spring.ai.vectorstore.pgvector.initialize-schema=false).
-- The column layout is the one PgVectorStore expects: id, content, metadata, embedding.
-- Each row is one chunk of text. metadata holds documentId, page and chunkIndex.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content   TEXT,
    metadata  JSON,
    -- 384 = output size of the default embedding model (all-MiniLM-L6-v2).
    -- A model with a different size needs a new migration that recreates this column.
    embedding VECTOR(384)
);

-- Approximate nearest-neighbour index for cosine similarity search.
CREATE INDEX vector_store_embedding_idx
    ON vector_store USING HNSW (embedding vector_cosine_ops);