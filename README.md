# AI Knowledge Assistant (RAG Backend)

Spring Boot backend for a document-aware Q&A system:
- Upload documents
- Chunk + embed content
- Store vectors (FAISS-mode local adapter or Pinecone)
- Retrieve top chunks
- Generate grounded answers using Gemini

## Stack
- Java 17+
- Spring Boot 3
- PostgreSQL (users/documents/chunks/chat/session + FTS)
- Redis (query/retrieval/document caches)
- Kafka (async document processing pipeline)
- Spring Actuator + Micrometer + Prometheus
- Gemini API (embeddings + answer generation)
- Vector provider:
  - `faiss` (default): local in-memory FAISS-mode adapter
  - `pinecone`: cloud vector index

## Implemented APIs
- `POST /auth/signup`
- `POST /auth/login`
- `POST /documents/upload` (multipart: `file`)
- `GET /documents`
- `POST /query`
- `GET /query/stream` (SSE)
- `POST /chat/session`
- `POST /chat/message`
- `GET /chat/history`

## Security and Platform
- JWT authentication
- BCrypt password hashing
- Validation + global error handling
- Basic IP rate limiting
- Kafka-based async document processing
- Flyway migrations

## Persistent Local Config (No Re-export Needed)

This project auto-loads a local `.env` file at startup.

One-time setup:
```bash
./scripts/init-env.sh
```

Then edit `.env` once and set your real values.

## Environment Variables (for `.env`)
```bash
DB_URL=jdbc:postgresql://localhost:5432/rag_assistant
DB_USERNAME=postgres
DB_PASSWORD=postgres

REDIS_HOST=localhost
REDIS_PORT=6379

KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
KAFKA_CONSUMER_GROUP=rag-assistant
KAFKA_TOPIC_DOCUMENT_UPLOADED=document-uploaded
KAFKA_TOPIC_DOCUMENT_PROCESSING_COMPLETED=document-processing-completed
KAFKA_TOPIC_DOCUMENT_PROCESSING_FAILED=document-processing-failed

JWT_SECRET=change-me-change-me-change-me-change-me
JWT_EXPIRATION_MINUTES=1440

UPLOAD_DIR=uploads

GEMINI_API_KEY=
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
GEMINI_CHAT_MODEL=gemini-2.5-flash

VECTOR_PROVIDER=faiss
RAG_TOP_K=5
RAG_QUERY_CACHE_TTL_HOURS=6
RAG_RETRIEVAL_CACHE_TTL_HOURS=1
RAG_DOCUMENT_CACHE_TTL_HOURS=24
RAG_VECTOR_WEIGHT=0.7
RAG_KEYWORD_WEIGHT=0.3
RAG_RETRIEVAL_TOP_K=10
RAG_MEMORY_MAX_MESSAGES=8
RAG_MEMORY_MAX_CHARS_TOTAL=3000
RAG_MEMORY_MAX_CHARS_PER_MESSAGE=600
RAG_MAX_CONTEXT_CHARS_PER_CHUNK=1400
RAG_CITATION_SNIPPET_CHARS=220

PINECONE_API_KEY=
PINECONE_INDEX_HOST=
PINECONE_NAMESPACE=default
```

## Run
1. Start PostgreSQL, Redis, and Kafka.
2. Create database `rag_assistant`.
3. Create `.env` once using `./scripts/init-env.sh` and fill values.
4. Run:
```bash
mvn spring-boot:run
```

## Easy API Smoke Test (One Command)

After the app is running, execute:

```bash
./scripts/smoke-test.sh
```

This validates Tier-1 flows end-to-end:
- multi-document query
- conversational memory (session + follow-up)
- source citations
- SSE streaming
- workspace query (without `documentIds`)

Optional:
```bash
BASE_URL=http://localhost:8080 QUERY_TEXT="Give me a short summary" ./scripts/smoke-test.sh
```

Use your own files:
```bash
./scripts/smoke-test.sh --files /abs/doc1.txt,/abs/doc2.txt
```

Dedicated stream check:
```bash
TOKEN=<jwt> SESSION_ID=<uuid> DOCUMENT_IDS_CSV=<doc1>,<doc2> ./scripts/test-stream.sh
```

## Granular Test Scripts

Auth only:
```bash
./scripts/test-auth.sh --email you@example.com --password Password123
```

Upload and wait for READY:
```bash
TOKEN=<jwt> ./scripts/test-upload.sh --files /abs/doc1.txt,/abs/doc2.txt
```

Direct query:
```bash
TOKEN=<jwt> DOCUMENT_IDS_CSV=<doc1>,<doc2> QUERY="Summarize key points" ./scripts/test-query.sh
```

Chat memory (2 turns):
```bash
TOKEN=<jwt> DOCUMENT_IDS_CSV=<doc1>,<doc2> ./scripts/test-chat.sh
```

## Notes
- File parsing supports `.pdf`, `.txt`, `.md`, `.csv`.
- Query responses include typed citations (`documentId`, `documentName`, `chunkId`, `page`, `snippet`).
- Hybrid retrieval combines vector similarity + PostgreSQL full-text search with weighted rank fusion.
- Observability endpoints:
  - `/actuator/health`
  - `/actuator/metrics`
  - `/actuator/prometheus`
  - `/metrics/custom`
- Grafana starter dashboards are in `monitoring/grafana/`.
