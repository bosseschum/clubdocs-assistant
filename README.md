# Clubdocs Assistant

A **Retrieval-Augmented Generation (RAG)** chat application that answers questions about club documents — statutes, fee regulations, meeting minutes, and more — by searching uploaded PDFs and generating grounded, cited answers.

Users upload PDF documents, and the system makes them searchable. They can then ask natural-language questions (in German) and receive answers with source citations pointing to the exact document and page.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Frontend (Angular 22)                                          │
│                                                                  │
│  ChatPage ──► ChatStore (signal-based state) ──► ChatApi        │
│  DocumentsPage ──► DocumentApi                                  │
│                                                                  │
│  Runs on port 4200 (dev), proxies /api to backend               │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP (REST, JSON)
┌────────────────────────────▼────────────────────────────────────┐
│  Backend (Spring Boot 4, Java 21)                               │
│                                                                  │
│  ChatController  ──► ChatService ──► RetrievalService           │
│                                    ──► PromptBuilder            │
│                                    ──► Spring AI ChatClient     │
│                                                                  │
│  DocumentController ──► DocumentService                         │
│                          ──► FileStorage                        │
│                          ──► DocumentRepository (JPA)           │
│                                                                  │
│  IngestionListener (async) ──► IngestionService                 │
│                              ──► TextNormalizer                 │
│                              ──► TextChunker                    │
│                              ──► Spring AI VectorStore          │
│                                                                  │
│  Runs on port 8080                                              │
└────────────────────────────┬────────────────────────────────────┘
                             │
┌────────────────────────────▼────────────────────────────────────┐
│  PostgreSQL 17 + pgvector (Docker)                              │
│                                                                  │
│  document table  — metadata (filename, status, size)            │
│  vector_store    — embeddings + metadata (documentId, page)     │
│  HNSW index on embeddings for cosine similarity search          │
└─────────────────────────────────────────────────────────────────┘
```

## How It Works

### Document Upload & Ingestion

1. User uploads a PDF via the frontend.
2. `DocumentController` → `DocumentService` saves metadata to the `document` table and stores the file on disk.
3. A `DocumentUploadedEvent` is published.
4. `IngestionListener` picks up the event **after the transaction commits** (`@TransactionalEventListener(phase = AFTER_COMMIT)`) and starts ingestion on a background thread (`@Async`).
5. `IngestionService` reads the PDF page by page, normalizes whitespace, splits text into overlapping chunks (800 chars, 100-char overlap), embeds each chunk (384-dim ONNX model), and stores the embeddings in pgvector.
6. Document status transitions: `UPLOADED` → `PROCESSING` → `READY` (or `FAILED` on error).

### Chat (RAG Pipeline)

1. User asks a question via the chat interface.
2. `ChatService.ask()` calls `RetrievalService.retrieve()` to find the top-3 most similar chunks (cosine similarity in pgvector).
3. `PromptBuilder.build()` constructs a prompt with:
   - System instructions (answer only from sources, cite with `[N]`, fallback text if not found).
   - Numbered source chunks wrapped in `<context>` tags.
   - The user's question.
4. The prompt is sent to the LLM (Qwen3.5:4B via Ollama).
5. The answer, together with the source chunks, is returned to the frontend.
6. The frontend renders citations as clickable links to the source documents.

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **Local LLM (Ollama + Qwen3.5:4B)** | No API keys or external dependencies; runs entirely on-premises. Suitable for club-scale workloads. |
| **ONNX embeddings (all-MiniLM-L6-v2)** | 384-dimensional embeddings run locally via Spring AI Transformers — no external embedding API needed. |
| **pgvector with HNSW index** | Approximate nearest-neighbor search in the same database as metadata — no separate vector DB to manage. |
| **Event-driven async ingestion** | Upload returns immediately; slow PDF parsing and embedding happen in the background. `AFTER_COMMIT` prevents race conditions. |
| **800-char chunks with 100-char overlap** | Balances embedding quality (model truncates ~256 tokens) with retrieval granularity. Overlap preserves context across chunk boundaries. |
| **Top-K = 3 for retrieval** | Trade-off between answer quality and latency. Each chunk costs ~15–20 s of prompt-reading time on CPU with a 4B model. |
| **Temperature = 0.1** | Low temperature reduces hallucinated details in RAG answers. |
| **Prompt injection protection** | Sources are wrapped in `<context>` tags and explicitly marked as data, not instructions. |
| **Grounding constraint** | Model is instructed to reply with a fixed fallback sentence if the answer is not in the sources. |
| **Flyway for schema management** | Database schema is version-controlled and migrated automatically. Spring AI's own schema init is disabled. |
| **Three-layer architecture** | Controllers (HTTP only) → Services (business logic) → Repositories/Storage (data access). Clear separation of concerns. |
| **Angular Signals for state** | `ChatStore` uses Angular signals for reactive, navigation-safe conversation state. |
| **RFC 9457 ProblemDetail** | Standardized error responses (`application/problem+json`) for consistent client-side error handling. |

## Tech Stack

### Backend
- **Spring Boot 4.1.1** with Java 21
- **Spring AI 2.0.1** (chat client, vector store, PDF reader)
- **Ollama** with Qwen3.5:4B model (local LLM)
- **Spring AI Transformers** (ONNX embedding model, 384 dimensions)
- **PostgreSQL 17** with **pgvector** extension
- **Flyway** for database migrations
- **Testcontainers** for integration tests

### Frontend
- **Angular 22** with Signals
- **Vitest** for unit tests
- **Node.js 24**

### Infrastructure
- **Docker Compose** for PostgreSQL + pgvector
- **GitHub Actions** for CI (backend + frontend)

## Setup & Running

### Prerequisites
- Java 21 (Temurin)
- Node.js 24
- Docker (for PostgreSQL and Testcontainers)
- Ollama with `qwen3.5:4b` model pulled

### Start the Database
```bash
docker compose up -d
```

### Start Ollama
Ensure Ollama is running and the model is available:
```bash
ollama pull qwen3.5:4b
```

### Run the Backend
```bash
cd backend
./mvnw spring-boot:run
```

### Run the Frontend
```bash
cd frontend
npm install
ng serve
```

Navigate to `http://localhost:4200/`.

## Testing

### Backend Tests

The backend has three tiers of tests:

1. **Unit tests** — fast, no database or LLM needed:
   - `TextChunkerTest` — chunking logic with overlap
   - `TextNormalizerTest` — whitespace normalization
   - `PromptBuilderTest` — prompt construction and injection protection
   - `ChatServiceTest` — orchestration with mocked LLM

2. **Integration tests** — use Testcontainers (Docker required):
   - `DocumentRepositoryTest` — JPA repository operations
   - `DocumentServiceTest` — service layer with embedded DB
   - `IngestionPipelineTest` — end-to-end PDF ingestion with real embeddings
   - `DocumentControllerTest` — HTTP layer with MockMvc
   - `ChatControllerTest` — HTTP layer for chat endpoint

3. **Evaluation harness** — not part of normal test run:
   - `EvaluationRun` — full RAG evaluation with sample documents

Run all tests:
```bash
cd backend
./mvnw verify
```

### Frontend Tests

```bash
cd frontend
ng test
```

Tests cover:
- `citations.spec.ts` — citation number extraction from answers
- `chat-page.spec.ts` — chat page behavior and navigation
- `format-size.spec.ts` — file size formatting
- `documents-page-polling.spec.ts` — document status polling

### CI Pipeline

GitHub Actions runs on every push to `main` and on pull requests:
- **Backend**: Java 21, Maven build with all tests (Testcontainers)
- **Frontend**: Node 24, unit tests (Vitest), production build

## Evaluation

The evaluation framework (`EvaluationRun`) measures RAG quality using a curated set of 26 questions across four categories:

| Category | Description | Count |
|----------|-------------|-------|
| `fact` | Straightforward factual questions | 20 |
| `confusable` | Questions with similar-looking answers (e.g., 3€ vs 5€ fees) | 5 |
| `conflict` | Questions where documents disagree (e.g., old vs new fee) | 1 |

### Retrieval Metrics (Latest Run: 2026-10-31)

| K | Hits | Percentage |
|---|------|------------|
| 1 | 10 | 38.5% |
| 2 | 14 | 53.8% |
| 3 | 16 | 61.5% |
| 4 | 17 | 65.4% |
| 6 | 20 | 76.9% |
| 8 | 22 | 84.6% |

**Hit@4 by Category:**

| Category | Hits | Total | Percentage |
|----------|------|-------|------------|
| fact | 13 | 20 | 65.0% |
| confusable | 3 | 5 | 60.0% |
| conflict | 1 | 1 | 100.0% |

**Misses (evidence not found in top-8):** 4 questions — mostly very specific numeric values (e.g., "Mahngebühr", "Sporthalle samstags") that are hard to match with semantic similarity alone.

### Running Evaluation

Retrieval-only (fast, no LLM needed):
```bash
cd backend
./mvnw test -Dtest=EvaluationRun
```

Full evaluation including LLM answers (requires Ollama):
```bash
cd backend
./mvnw test -Dtest=EvaluationRun -Deval.answers=true
```

Reports are written to `backend/target/eval/`.

### Evaluation Design

- **Sample documents**: 6 realistic German club documents (statute, fee regulation, hall rules, meeting minutes, travel expense policy, data privacy).
- **Question format**: Each question has `mustContain` strings (required in answer), `niceToHave` strings (bonus), `evidence` text (for retrieval grading), and `expectFallback` flag (for unanswerable questions).
- **Grading**: `EvalChecks` provides pure utility methods for hit@k retrieval scoring, answer correctness (mustContain), fallback detection, and invalid citation counting.

## Project Structure

```
clubdocs-assistant/
├── backend/
│   ├── src/main/java/dev/bosse/clubdocs/
│   │   ├── chat/              # RAG chat pipeline
│   │   │   ├── ChatController.java      # POST /api/chat
│   │   │   ├── ChatService.java         # Orchestrate: retrieve → prompt → answer
│   │   │   ├── RetrievalService.java    # Vector similarity search (top-K)
│   │   │   ├── PromptBuilder.java       # Prompt construction with grounding
│   │   │   ├── ChatAnswer.java          # Response DTO
│   │   │   ├── ChatRequest.java         # Request DTO
│   │   │   ├── RetrievedChunk.java      # Chunk with filename and page
│   │   │   └── ChatUnavailableException.java
│   │   ├── document/          # Document management
│   │   │   ├── Document.java            # JPA entity
│   │   │   ├── DocumentController.java  # GET/POST/DELETE /api/documents
│   │   │   ├── DocumentService.java     # Business logic
│   │   │   ├── DocumentRepository.java  # JPA repository
│   │   │   ├── DocumentResponse.java    # DTO
│   │   │   ├── DocumentStatus.java      # Enum: UPLOADED, PROCESSING, READY, FAILED
│   │   │   ├── DocumentUploadedEvent.java
│   │   │   ├── DocumentDeletedEvent.java
│   │   │   └── DocumentNotFoundException.java
│   │   ├── ingestion/         # PDF ingestion pipeline
│   │   │   ├── IngestionService.java    # PDF → chunks → embeddings → vector store
│   │   │   ├── IngestionListener.java   # Async event handler
│   │   │   ├── TextChunker.java         # Overlapping chunk splitting
│   │   │   └── TextNormalizer.java      # Whitespace cleanup
│   │   ├── storage/           # File storage
│   │   │   └── FileStorage.java         # Disk-based file storage
│   │   ├── config/            # Configuration
│   │   │   └── AsyncConfig.java         # Enable @Async
│   │   └── common/            # Cross-cutting
│   │       └── ApiExceptionHandler.java # RFC 9457 error responses
│   ├── src/main/resources/
│   │   ├── application.yml            # Configuration
│   │   └── db/migration/              # Flyway migrations
│   │       ├── V1__create_document_table.sql
│   │       └── V2__create___vector__store.sql
│   └── src/test/
│       ├── java/dev/bosse/clubdocs/
│       │   ├── chat/                  # Chat unit tests
│       │   ├── document/              # Document tests
│       │   ├── ingestion/             # Ingestion tests + pipeline test
│       │   └── eval/                  # Evaluation harness
│       │       ├── EvaluationRun.java
│       │       ├── EvalQuestion.java
│       │       └── EvalChecks.java
│       └── resources/
│           ├── eval/questions.json    # Evaluation questions
│           └── sample/                # Sample PDFs for tests
│
├── frontend/
│   └── src/app/
│       ├── chat/                      # Chat UI
│       │   ├── chat-page.ts           # Main chat container
│       │   ├── chat-input.ts          # Question input component
│       │   ├── chat-message.ts        # Message display with citations
│       │   └── citations.ts           # Citation number extraction
│       ├── core/                      # Shared services
│       │   ├── chat-api.ts            # HTTP client for chat
│       │   ├── chat-store.ts          # Signal-based conversation state
│       │   ├── chat.model.ts          # TypeScript models
│       │   ├── document-api.ts        # HTTP client for documents
│       │   └── document.model.ts      # Document TypeScript models
│       └── documents/                 # Document management UI
│           ├── document-list.ts       # Document list view
│           ├── document-upload.ts     # Upload component
│           └── format-size.ts         # File size formatting
│
├── docker-compose.yml                 # PostgreSQL + pgvector
└── .github/workflows/ci.yml           # CI pipeline
```

## Configuration

Key settings in `backend/src/main/resources/application.yml`:

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.ollama.chat.model` | `qwen3.5:4b` | LLM model name |
| `spring.ai.ollama.chat.num-ctx` | `4096` | Context window size |
| `spring.ai.ollama.chat.temperature` | `0.1` | Low for factual answers |
| `spring.ai.vectorstore.pgvector.dimensions` | `384` | Embedding dimensions |
| `spring.ai.vectorstore.pgvector.index-type` | `HNSW` | Vector index type |
| `clubdocs.storage.dir` | `./data/uploads` | PDF storage directory |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/documents` | Upload a PDF (multipart/form-data) |
| `GET` | `/api/documents` | List all documents |
| `DELETE` | `/api/documents/{id}` | Delete a document and its chunks |
| `POST` | `/api/chat` | Ask a question (JSON body: `{"question": "..."}`) |

## Future Work

- **Hybrid search**: Combine vector similarity with keyword (BM25) search to improve recall for specific terms.
- **Reranking**: Add a cross-encoder reranker to improve top-K quality.
- **Chunk tuning**: Experiment with chunk size and overlap based on evaluation results.
- **Prompt tuning**: Refine system instructions for better answer quality.
- **Answer evaluation**: Automate the answer-stage evaluation with a judge model.
- **Document viewer**: Add inline PDF preview with highlighted source passages.
