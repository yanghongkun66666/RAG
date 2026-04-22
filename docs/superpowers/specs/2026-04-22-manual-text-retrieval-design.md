# learn/01-basic-rag-text Design

## Background

This branch implements the first real RAG learning slice after the shell stage.
The goal is not file upload, dataset management, or QA generation.
The goal is to let the user observe the smallest practical chain:

`manual raw text -> chunking -> embedding -> pgvector -> semantic retrieval`

This branch stays inside the learning project:

- Main project: `D:\Java\Projects\Finished Projects\AgentX-RAG-Learn`
- Reference project: `D:\Java\Projects\Finished Projects\AgentX-Newest`

The reference project is read-only and is used only to borrow architecture ideas around:

- OpenAI-compatible embedding configuration
- pgvector store integration
- `TextSegment` plus metadata retrieval flow

## Aim

After this branch, the user should be able to paste a piece of raw text into the retrieval workbench, trigger indexing, inspect the generated chunks, ask a question, and explain why specific chunks were retrieved.

## In Scope

- A retrieval workbench page for manual text experiments
- Manual submission of a raw text block
- Simple, explainable chunking rules
- Real embedding calls through SiliconFlow's OpenAI-compatible API
- Model: `Qwen/Qwen3-Embedding-8B`
- Embedding dimension: `4096`
- pgvector-backed semantic search scoped to one experiment
- Visible display of:
  - original text
  - generated chunks
  - topK retrieval results

## Out of Scope

- File upload
- `dataset`, `file_detail`, `document_unit`
- MQ or async processing
- answer generation
- keyword retrieval
- hybrid retrieval
- RRF
- rerank
- HyDE
- query expansion

## Design Principles

1. Keep the branch focused on one learning objective.
2. Separate business data from vector index data.
3. Make every step observable in the UI.
4. Keep the backend flow close to AgentX where it matters:
   - OpenAI-compatible embedding model
   - pgvector store
   - metadata-filtered semantic retrieval
5. Avoid introducing future-stage abstractions early.

## User Experience

The feature lives in the existing retrieval workbench page.

The page is a single workbench, not a multi-page flow.
It has three visible zones:

1. **Manual Text Input**
   - text title or note
   - raw text textarea
   - button to index the text

2. **Chunk Observation**
   - generated chunk count
   - ordered chunk list
   - each chunk shows index and content

3. **Retrieval Experiment**
   - question input
   - topK input
   - retrieve button
   - ordered result list with score, chunk index, and chunk content

This page must make the learning chain explicit.
The user should not have to guess whether chunking happened or whether retrieval used the expected data.

## Architecture

### Backend Responsibilities

- `ManualTextRetrievalController`
  - HTTP entry for indexing and retrieval

- `ManualTextRetrievalAppService`
  - orchestrates the full branch use case
  - owns the two main workflows:
    - index manual text
    - search within one manual text experiment

- `SimpleTextChunker`
  - applies minimal chunking rules
  - deterministic and easy to explain

- `EmbeddingProperties`
  - binds SiliconFlow OpenAI-compatible configuration

- `EmbeddingConfig`
  - creates the `EmbeddingStore<TextSegment>` backed by pgvector

- `EmbeddingModelFactory`
  - creates `OpenAiEmbeddingModel` using configured base URL, API key, and model name

### Frontend Responsibilities

- retrieval workbench page
  - submits indexing request
  - renders chunk list from backend response
  - submits retrieval request
  - renders retrieval results

The frontend should stay thin.
Business flow and integration logic belong in the backend.

## Data Model

This branch uses two business tables plus one vector table.

### 1. `manual_text_experiment`

Represents one manual indexing experiment.

Suggested fields:

- `id`
- `title`
- `raw_text`
- `chunk_count`
- `created_at`
- `updated_at`

Purpose:

- keeps the original source text
- gives retrieval a clear experiment boundary
- lets the UI show what was indexed

### 2. `manual_text_chunk`

Represents the business-level chunk after splitting.

Suggested fields:

- `id`
- `experiment_id`
- `chunk_index`
- `content`
- `created_at`

Purpose:

- stores the actual chunk text the user wants to inspect
- separates business chunks from vector index implementation

### 3. `vector_store`

Used only for vector search.

It stores embeddings plus metadata.
Metadata must include at least:

- `experimentId`
- `chunkId`
- `chunkIndex`

This keeps the design aligned with the later AgentX-style separation:

- business text is not the vector table
- vector search data is not the source-of-truth business model

## Chunking Strategy

Chunking must stay simple and explainable.

Rules:

1. Normalize line endings and trim leading or trailing blank space.
2. Split the raw text by blank lines first.
3. For segments still longer than the configured chunk size, split them by a fixed character window.
4. Keep a small overlap between adjacent windows.
5. Drop empty chunks after trimming.
6. Preserve chunk order using `chunk_index`.

Initial defaults:

- chunk size: `500` characters
- overlap: `100` characters

These values are not meant to be globally optimal.
They are meant to be easy to reason about for a learning branch.

## Embedding Integration

This branch uses SiliconFlow through the OpenAI-compatible embedding interface.

Required configuration:

- `EMBEDDING_API_KEY`
- `EMBEDDING_BASE_URL`
- `EMBEDDING_MODEL=Qwen/Qwen3-Embedding-8B`
- `EMBEDDING_DIMENSION=4096`
- `VECTOR_STORE_TABLE=public.vector_store`

The backend uses:

- `OpenAiEmbeddingModel` for request generation
- `PgVectorEmbeddingStore` for embedding persistence and search

Each chunk becomes a `TextSegment` with metadata.
That metadata is what allows search results to be mapped back to `manual_text_chunk`.

## Indexing Flow

### Request

`POST /api/v1/retrieval/manual-text/index`

Input:

- `title` optional
- `rawText` required

### Processing

1. Validate that `rawText` is not blank.
2. Create a `manual_text_experiment` record.
3. Chunk the text using `SimpleTextChunker`.
4. Save all generated `manual_text_chunk` rows.
5. For each chunk:
   - build `TextSegment`
   - attach metadata with experiment and chunk identity
   - generate embedding using `Qwen/Qwen3-Embedding-8B`
   - add to `vector_store`
6. Update `chunk_count` on the experiment.
7. Return the experiment summary and chunk list.

### Response

Response payload should include:

- `experimentId`
- `title`
- `rawText`
- `chunkCount`
- `chunks`
  - `chunkId`
  - `chunkIndex`
  - `content`

## Retrieval Flow

### Request

`POST /api/v1/retrieval/manual-text/search`

Input:

- `experimentId` required
- `question` required
- `topK` optional, default `3`

### Processing

1. Validate that `experimentId` exists.
2. Validate that `question` is not blank.
3. Generate query embedding using the same embedding model.
4. Search `vector_store` with metadata filtered to the current `experimentId`.
5. Map matches back to `manual_text_chunk`.
6. Return ordered topK results with score and chunk details.

### Response

Response payload should include:

- `experimentId`
- `question`
- `topK`
- `results`
  - `score`
  - `chunkId`
  - `chunkIndex`
  - `content`

## Error Handling

The branch must make at least these failure paths observable:

### Blank raw text

- indexing request is rejected
- return clear validation error

### Blank question

- retrieval request is rejected
- return clear validation error

### Missing experiment

- retrieval request returns a clear business error

### Missing embedding configuration

- indexing or retrieval returns a clear configuration error
- do not expose secrets in the response

### Embedding API failure

- return a stable error response
- log the failure with enough context to debug
- do not leave the user guessing whether the request silently succeeded

## API Shape

The project already uses a unified response wrapper.
These endpoints should stay inside that convention.

Suggested controller routes:

- `POST /v1/retrieval/manual-text/index`
- `POST /v1/retrieval/manual-text/search`

Because the application already has `/api` as context path, the final visible endpoints are:

- `/api/v1/retrieval/manual-text/index`
- `/api/v1/retrieval/manual-text/search`

## Persistence and Migration

Add one Flyway migration for this branch.

It should:

- create `manual_text_experiment`
- create `manual_text_chunk`
- ensure pgvector extension exists if not already present

The vector table itself should stay aligned with the pgvector store configuration.
Do not introduce future-stage tables in this migration.

## Testing and Validation

This branch needs both execution validation and learning validation.

### Functional Validation

1. Paste a text sample and index it successfully.
2. Verify that chunks are visible in the response and on the page.
3. Verify `manual_text_chunk` row count matches chunk count.
4. Ask a question that clearly matches one chunk.
5. Verify topK results include the expected chunk near the top.

### Failure Validation

1. Submit blank raw text and confirm validation error.
2. Submit blank question and confirm validation error.
3. Remove or invalidate embedding config and confirm a stable integration error.

### Learning Validation

After implementation, the user should be able to answer:

1. Where is the original text stored?
2. Where are chunks stored?
3. Why is `vector_store` not the business source of truth?
4. How does the system limit retrieval to one experiment?
5. Why was a certain chunk retrieved for a given question?

## Non-Goals for This Branch

The branch intentionally does not optimize for:

- production-scale indexing throughput
- large document handling
- async resilience
- retrieval quality enhancements
- reuse of future dataset abstractions

Those belong to later branches in the plan.

## Implementation Notes

- Reuse existing response and exception handling patterns.
- Keep teaching-style comments where the code would otherwise be opaque.
- Prefer small focused classes instead of one large service.
- Keep the retrieval page explicit and practical, not decorative.

## Branch Deliverable

The branch is complete when:

1. `learn/01-basic-rag-text` has a working retrieval workbench page.
2. Manual text can be indexed with real embeddings.
3. Chunks are visible to the user.
4. Semantic retrieval returns topK chunk matches from the same experiment.
5. Validation notes and review notes can be written against an observable chain.
