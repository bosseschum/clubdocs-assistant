-- 1. Enable the pgvector extension (required for the vector data type)
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. Create the document table
CREATE TABLE document (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'UPLOADED', -- UPLOADED, PROCESSING, READY, FAILED
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create the chunk table (linked to document via foreign key)
CREATE TABLE chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES document(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    page_number INT,
    text TEXT NOT NULL,
    embedding VECTOR(1536), -- Adjust dimensions based on your model (e.g., 1536 for OpenAI text-embedding-3-small)
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Add performance indexes
CREATE INDEX idx_chunks_document_id ON chunks(document_id);
