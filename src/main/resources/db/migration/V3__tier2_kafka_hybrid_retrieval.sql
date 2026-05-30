CREATE TABLE IF NOT EXISTS document_processing_jobs (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doc_jobs_document_created
    ON document_processing_jobs(document_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_doc_jobs_status
    ON document_processing_jobs(status);

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS search_vector tsvector;

UPDATE document_chunks
SET search_vector = to_tsvector('english', coalesce(text, ''))
WHERE search_vector IS NULL;

CREATE INDEX IF NOT EXISTS idx_chunks_search
    ON document_chunks
    USING GIN(search_vector);

CREATE OR REPLACE FUNCTION document_chunks_search_vector_trigger()
RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english', coalesce(NEW.text, ''));
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_document_chunks_search_vector ON document_chunks;

CREATE TRIGGER trg_document_chunks_search_vector
BEFORE INSERT OR UPDATE OF text ON document_chunks
FOR EACH ROW
EXECUTE FUNCTION document_chunks_search_vector_trigger();
