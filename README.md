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
- PostgreSQL (users/documents/chunks/chat history)
- Redis (query response cache)
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
- `GET /chat/history`

## Security and Platform
- JWT authentication
- BCrypt password hashing
- Validation + global error handling
- Basic IP rate limiting
- Async document processing
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

JWT_SECRET=change-me-change-me-change-me-change-me
JWT_EXPIRATION_MINUTES=1440

UPLOAD_DIR=uploads

GEMINI_API_KEY=
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
GEMINI_CHAT_MODEL=gemini-2.5-flash

VECTOR_PROVIDER=faiss
RAG_TOP_K=5
RAG_CACHE_TTL_HOURS=4

PINECONE_API_KEY=
PINECONE_INDEX_HOST=
PINECONE_NAMESPACE=default
```

## Run
1. Start PostgreSQL and Redis.
2. Create database `rag_assistant`.
3. Create `.env` once using `./scripts/init-env.sh` and fill values.
4. Run:
```bash
mvn spring-boot:run
```

## Easy API Smoke Test (One Command)

After the app is running, execute:

```bash
./scripts/smoke-test.sh --file /absolute/path/to/your/document.txt
```

If you skip `--file`, the script auto-creates `/tmp/rag-smoke-test.txt`.

Optional:
```bash
BASE_URL=http://localhost:8080 QUERY_TEXT="Give me a short summary" ./scripts/smoke-test.sh
```

## Notes
- File parsing supports `.pdf`, `.txt`, `.md`, `.csv`.
- Prompt template in query flow follows the provided spec.
- Source references in response are returned as `documentId:chunkIndex`.
