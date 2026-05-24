ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS page_number INT,
    ADD COLUMN IF NOT EXISTS chunk_uuid UUID;

UPDATE document_chunks
SET chunk_uuid = id
WHERE chunk_uuid IS NULL;

ALTER TABLE document_chunks
    ALTER COLUMN chunk_uuid SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_document_chunks_chunk_uuid'
    ) THEN
        ALTER TABLE document_chunks
            ADD CONSTRAINT uk_document_chunks_chunk_uuid UNIQUE (chunk_uuid);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS chat_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_sessions_user_id_created_at
    ON chat_sessions(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_session_id_created_at
    ON chat_messages(session_id, created_at ASC);
