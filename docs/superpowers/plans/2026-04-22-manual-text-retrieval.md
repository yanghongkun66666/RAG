# Manual Text Retrieval Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real RAG learning slice where a user pastes raw text, indexes it with SiliconFlow embeddings, and retrieves topK chunks from the same experiment in the retrieval workbench.

**Architecture:** Keep business data and vector data separate. Store raw text and chunks in PostgreSQL business tables, store embeddings in pgvector-backed `vector_store`, and let the frontend hold the current `experimentId` in page state instead of asking the user to type it. The backend stays close to AgentX where it matters: OpenAI-compatible embedding config, `TextSegment` metadata, and metadata-filtered semantic retrieval.

**Tech Stack:** Spring Boot 3.3, JdbcTemplate, Flyway, PostgreSQL + pgvector, LangChain4j OpenAI-compatible embeddings, Next.js 15, React 19, TypeScript.

---

### Task 1: Chunking Domain and Test-First Behavior

**Files:**
- Create: `backend/src/test/java/org/xhy/raglearn/domain/retrieval/service/SimpleTextChunkerTest.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextChunkDraft.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/service/SimpleTextChunker.java`

- [ ] **Step 1: Write the failing chunker test**

```java
package org.xhy.raglearn.domain.retrieval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

class SimpleTextChunkerTest {

    private final SimpleTextChunker chunker = new SimpleTextChunker(20, 5);

    @Test
    void splits_on_blank_lines_before_falling_back_to_windows() {
        String rawText = """
                Spring Boot makes it easy to build Java apps.

                pgvector stores embeddings inside PostgreSQL.
                """;

        List<ManualTextChunkDraft> chunks = chunker.split(rawText);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).content()).contains("Spring Boot");
        assertThat(chunks.get(1).content()).contains("pgvector");
    }

    @Test
    void creates_overlapping_windows_for_long_paragraphs() {
        String rawText = "abcdefghijklmnopqrstuvwxy";

        List<ManualTextChunkDraft> chunks = chunker.split(rawText);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("abcdefghijklmnopqrst");
        assertThat(chunks.get(1).content()).isEqualTo("pqrstuvwxy");
    }

    @Test
    void returns_empty_list_for_blank_input() {
        assertThat(chunker.split("   \n\n  ")).isEmpty();
    }
}
```

- [ ] **Step 2: Run the chunker test and confirm it fails**

Run:

```powershell
backend\mvnw.cmd -Dtest=SimpleTextChunkerTest test
```

Expected:

- build fails because `SimpleTextChunker` and `ManualTextChunkDraft` do not exist yet

- [ ] **Step 3: Implement the minimal chunker**

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextChunkDraft.java`

```java
package org.xhy.raglearn.domain.retrieval.model;

/**
 * Draft chunk before it is persisted.
 */
public record ManualTextChunkDraft(int chunkIndex, String content) {
}
```

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/service/SimpleTextChunker.java`

```java
package org.xhy.raglearn.domain.retrieval.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.StringUtils;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

/**
 * Simple, deterministic chunker for the first learning-stage retrieval flow.
 */
public class SimpleTextChunker {

    private final int chunkSize;
    private final int overlap;

    public SimpleTextChunker() {
        this(500, 100);
    }

    public SimpleTextChunker(int chunkSize, int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<ManualTextChunkDraft> split(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return List.of();
        }

        String normalized = rawText.replace("\r\n", "\n").trim();
        String[] paragraphs = normalized.split("\\n\\s*\\n");

        List<ManualTextChunkDraft> chunks = new ArrayList<>();
        int chunkIndex = 0;
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }

            if (trimmed.length() <= chunkSize) {
                chunks.add(new ManualTextChunkDraft(chunkIndex++, trimmed));
                continue;
            }

            int start = 0;
            while (start < trimmed.length()) {
                int end = Math.min(start + chunkSize, trimmed.length());
                String window = trimmed.substring(start, end).trim();
                if (StringUtils.hasText(window)) {
                    chunks.add(new ManualTextChunkDraft(chunkIndex++, window));
                }
                if (end == trimmed.length()) {
                    break;
                }
                start = Math.max(end - overlap, start + 1);
            }
        }

        return chunks;
    }
}
```

- [ ] **Step 4: Run the chunker test and confirm it passes**

Run:

```powershell
backend\mvnw.cmd -Dtest=SimpleTextChunkerTest test
```

Expected:

- `BUILD SUCCESS`
- `SimpleTextChunkerTest` passes all three cases

- [ ] **Step 5: Commit the chunker unit**

```powershell
git add backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextChunkDraft.java `
        backend/src/main/java/org/xhy/raglearn/domain/retrieval/service/SimpleTextChunker.java `
        backend/src/test/java/org/xhy/raglearn/domain/retrieval/service/SimpleTextChunkerTest.java
git commit -m "feat(rag-learn): add manual text chunker"
```

### Task 2: Indexing Flow, Persistence Ports, and Database Wiring

**Files:**
- Modify: `backend/pom.xml`
- Modify: `.env.example`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/org/xhy/raglearn/common/exception/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/org/xhy/raglearn/common/exception/BusinessException.java`
- Create: `backend/src/main/resources/db/migration/V2__manual_text_retrieval.sql`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextExperiment.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextChunk.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ChunkSearchMatch.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/repository/ManualTextExperimentRepository.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/repository/ManualTextChunkRepository.java`
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/gateway/ManualTextVectorGateway.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextIndexCommand.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextChunkView.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextIndexResult.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/ManualTextRetrievalAppService.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/JdbcManualTextExperimentRepository.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/JdbcManualTextChunkRepository.java`
- Create: `backend/src/test/java/org/xhy/raglearn/application/retrieval/ManualTextIndexingAppServiceTest.java`

- [ ] **Step 1: Write the failing indexing service test**

```java
package org.xhy.raglearn.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexResult;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

class ManualTextIndexingAppServiceTest {

    @Test
    void indexes_manual_text_and_stores_all_chunks() {
        InMemoryExperimentRepository experimentRepository = new InMemoryExperimentRepository();
        InMemoryChunkRepository chunkRepository = new InMemoryChunkRepository();
        RecordingVectorGateway vectorGateway = new RecordingVectorGateway();
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                experimentRepository,
                chunkRepository,
                vectorGateway,
                new SimpleTextChunker(200, 20)
        );

        ManualTextIndexResult result = service.indexManualText(new ManualTextIndexCommand(
                "RAG Intro",
                "Spring Boot builds services.\n\npgvector stores embeddings."
        ));

        assertThat(result.chunkCount()).isEqualTo(2);
        assertThat(result.chunks()).hasSize(2);
        assertThat(result.experimentId()).isNotNull();
        assertThat(chunkRepository.findByExperimentId(result.experimentId())).hasSize(2);
        assertThat(vectorGateway.indexedChunkIds).hasSize(2);
        assertThat(experimentRepository.findById(result.experimentId()).orElseThrow().chunkCount()).isEqualTo(2);
    }

    @Test
    void rejects_blank_raw_text() {
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                new InMemoryExperimentRepository(),
                new InMemoryChunkRepository(),
                new RecordingVectorGateway(),
                new SimpleTextChunker(200, 20)
        );

        assertThatThrownBy(() -> service.indexManualText(new ManualTextIndexCommand("Empty", "   ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("raw text");
    }

    private static final class InMemoryExperimentRepository implements ManualTextExperimentRepository {
        private final Map<Long, ManualTextExperiment> store = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public ManualTextExperiment create(String title, String rawText) {
            ManualTextExperiment experiment = new ManualTextExperiment(sequence++, title, rawText, 0);
            store.put(experiment.id(), experiment);
            return experiment;
        }

        @Override
        public void updateChunkCount(long experimentId, int chunkCount) {
            ManualTextExperiment current = store.get(experimentId);
            store.put(experimentId, new ManualTextExperiment(current.id(), current.title(), current.rawText(), chunkCount));
        }

        @Override
        public Optional<ManualTextExperiment> findById(long experimentId) {
            return Optional.ofNullable(store.get(experimentId));
        }
    }

    private static final class InMemoryChunkRepository implements ManualTextChunkRepository {
        private final Map<Long, List<ManualTextChunk>> store = new LinkedHashMap<>();
        private long sequence = 1L;

        @Override
        public List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts) {
            List<ManualTextChunk> chunks = new ArrayList<>();
            for (ManualTextChunkDraft draft : drafts) {
                chunks.add(new ManualTextChunk(sequence++, experimentId, draft.chunkIndex(), draft.content()));
            }
            store.put(experimentId, chunks);
            return chunks;
        }

        @Override
        public List<ManualTextChunk> findByExperimentId(long experimentId) {
            return store.getOrDefault(experimentId, List.of());
        }
    }

    private static final class RecordingVectorGateway implements ManualTextVectorGateway {
        private final List<Long> indexedChunkIds = new ArrayList<>();

        @Override
        public void storeChunk(ManualTextChunk chunk) {
            indexedChunkIds.add(chunk.id());
        }

        @Override
        public List<org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch> search(long experimentId, String question, int topK) {
            return List.of();
        }
    }
}
```

- [ ] **Step 2: Run the indexing test and confirm it fails**

Run:

```powershell
backend\mvnw.cmd -Dtest=ManualTextIndexingAppServiceTest test
```

Expected:

- build fails because the retrieval domain, ports, DTOs, and service do not exist yet

- [ ] **Step 3: Implement the indexing backend**

Add LangChain4j dependencies in `backend/pom.xml`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.13.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
    <version>1.13.0-beta23</version>
</dependency>
```

Add embedding env vars to `.env.example`:

```dotenv
# Embedding
EMBEDDING_API_KEY=
EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_MODEL=Qwen/Qwen3-Embedding-0.6B
EMBEDDING_DIMENSION=1024
VECTOR_STORE_TABLE=public.vector_store
```

Add binding config to `backend/src/main/resources/application.yml`:

```yaml
embedding:
  api-key: ${EMBEDDING_API_KEY:}
  base-url: ${EMBEDDING_BASE_URL:https://api.siliconflow.cn/v1}
  model: ${EMBEDDING_MODEL:Qwen/Qwen3-Embedding-0.6B}
  dimension: ${EMBEDDING_DIMENSION:1024}
  vector-store-table: ${VECTOR_STORE_TABLE:public.vector_store}
```

Create `backend/src/main/java/org/xhy/raglearn/common/exception/BusinessException.java`:

```java
package org.xhy.raglearn.common.exception;

public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
```

Update `backend/src/main/java/org/xhy/raglearn/common/exception/GlobalExceptionHandler.java`:

```java
@ExceptionHandler(BusinessException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ApiResponse<Void> handleBusinessException(BusinessException exception) {
    return ApiResponse.failure("BUSINESS_ERROR", exception.getMessage());
}
```

Create Flyway migration `backend/src/main/resources/db/migration/V2__manual_text_retrieval.sql`:

```sql
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
    experiment_id BIGINT NOT NULL REFERENCES manual_text_experiment(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_manual_text_chunk_experiment_id
    ON manual_text_chunk (experiment_id);
```

Create domain records:

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextExperiment.java`

```java
package org.xhy.raglearn.domain.retrieval.model;

public record ManualTextExperiment(Long id, String title, String rawText, int chunkCount) {
}
```

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ManualTextChunk.java`

```java
package org.xhy.raglearn.domain.retrieval.model;

public record ManualTextChunk(Long id, Long experimentId, int chunkIndex, String content) {
}
```

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ChunkSearchMatch.java`

```java
package org.xhy.raglearn.domain.retrieval.model;

public record ChunkSearchMatch(double score, Long chunkId, int chunkIndex, String content) {
}
```

Create ports:

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/repository/ManualTextExperimentRepository.java`

```java
package org.xhy.raglearn.domain.retrieval.repository;

import java.util.Optional;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;

public interface ManualTextExperimentRepository {

    ManualTextExperiment create(String title, String rawText);

    void updateChunkCount(long experimentId, int chunkCount);

    Optional<ManualTextExperiment> findById(long experimentId);
}
```

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/repository/ManualTextChunkRepository.java`

```java
package org.xhy.raglearn.domain.retrieval.repository;

import java.util.List;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

public interface ManualTextChunkRepository {

    List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts);

    List<ManualTextChunk> findByExperimentId(long experimentId);
}
```

`backend/src/main/java/org/xhy/raglearn/domain/retrieval/gateway/ManualTextVectorGateway.java`

```java
package org.xhy.raglearn.domain.retrieval.gateway;

import java.util.List;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;

public interface ManualTextVectorGateway {

    void storeChunk(ManualTextChunk chunk);

    List<ChunkSearchMatch> search(long experimentId, String question, int topK);
}
```

Create indexing DTOs:

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextIndexCommand.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualTextIndexCommand(String title, @NotBlank String rawText) {
}
```

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextChunkView.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

public record ManualTextChunkView(Long chunkId, int chunkIndex, String content) {
}
```

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextIndexResult.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

import java.util.List;

public record ManualTextIndexResult(
        Long experimentId,
        String title,
        String rawText,
        int chunkCount,
        List<ManualTextChunkView> chunks
) {
}
```

Create `backend/src/main/java/org/xhy/raglearn/application/retrieval/ManualTextRetrievalAppService.java`:

```java
package org.xhy.raglearn.application.retrieval;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.xhy.raglearn.application.retrieval.dto.ManualTextChunkView;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexResult;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

@Service
public class ManualTextRetrievalAppService {

    private final ManualTextExperimentRepository experimentRepository;
    private final ManualTextChunkRepository chunkRepository;
    private final ManualTextVectorGateway vectorGateway;
    private final SimpleTextChunker chunker;

    public ManualTextRetrievalAppService(
            ManualTextExperimentRepository experimentRepository,
            ManualTextChunkRepository chunkRepository,
            ManualTextVectorGateway vectorGateway,
            SimpleTextChunker chunker
    ) {
        this.experimentRepository = experimentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorGateway = vectorGateway;
        this.chunker = chunker;
    }

    @Transactional
    public ManualTextIndexResult indexManualText(ManualTextIndexCommand command) {
        if (!StringUtils.hasText(command.rawText())) {
            throw new BusinessException("Manual text raw text must not be blank.");
        }

        var experiment = experimentRepository.create(command.title(), command.rawText());
        List<ManualTextChunk> chunks = chunkRepository.saveAll(experiment.id(), chunker.split(command.rawText()));
        for (ManualTextChunk chunk : chunks) {
            vectorGateway.storeChunk(chunk);
        }
        experimentRepository.updateChunkCount(experiment.id(), chunks.size());

        return new ManualTextIndexResult(
                experiment.id(),
                command.title(),
                command.rawText(),
                chunks.size(),
                chunks.stream()
                        .map(chunk -> new ManualTextChunkView(chunk.id(), chunk.chunkIndex(), chunk.content()))
                        .toList()
        );
    }
}
```

Create JDBC repositories:

`backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/JdbcManualTextExperimentRepository.java`

```java
package org.xhy.raglearn.infrastructure.retrieval;

import java.sql.PreparedStatement;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;

@Repository
public class JdbcManualTextExperimentRepository implements ManualTextExperimentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcManualTextExperimentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ManualTextExperiment create(String title, String rawText) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO manual_text_experiment (title, raw_text) VALUES (?, ?)",
                    new String[]{"id"}
            );
            statement.setString(1, title);
            statement.setString(2, rawText);
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        return new ManualTextExperiment(id.longValue(), title, rawText, 0);
    }

    @Override
    public void updateChunkCount(long experimentId, int chunkCount) {
        jdbcTemplate.update(
                "UPDATE manual_text_experiment SET chunk_count = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                chunkCount,
                experimentId
        );
    }

    @Override
    public Optional<ManualTextExperiment> findById(long experimentId) {
        return jdbcTemplate.query(
                "SELECT id, title, raw_text, chunk_count FROM manual_text_experiment WHERE id = ?",
                (rs, rowNum) -> new ManualTextExperiment(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getString("raw_text"),
                        rs.getInt("chunk_count")
                ),
                experimentId
        ).stream().findFirst();
    }
}
```

`backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/JdbcManualTextChunkRepository.java`

```java
package org.xhy.raglearn.infrastructure.retrieval;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;

@Repository
public class JdbcManualTextChunkRepository implements ManualTextChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcManualTextChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts) {
        List<ManualTextChunk> chunks = new ArrayList<>();
        for (ManualTextChunkDraft draft : drafts) {
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO manual_text_chunk (experiment_id, chunk_index, content) VALUES (?, ?, ?)",
                        new String[]{"id"}
                );
                statement.setLong(1, experimentId);
                statement.setInt(2, draft.chunkIndex());
                statement.setString(3, draft.content());
                return statement;
            }, keyHolder);
            chunks.add(new ManualTextChunk(keyHolder.getKey().longValue(), experimentId, draft.chunkIndex(), draft.content()));
        }
        return chunks;
    }

    @Override
    public List<ManualTextChunk> findByExperimentId(long experimentId) {
        return jdbcTemplate.query(
                "SELECT id, experiment_id, chunk_index, content FROM manual_text_chunk WHERE experiment_id = ? ORDER BY chunk_index ASC",
                (rs, rowNum) -> new ManualTextChunk(
                        rs.getLong("id"),
                        rs.getLong("experiment_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("content")
                ),
                experimentId
        );
    }
}
```

- [ ] **Step 4: Run the indexing tests and confirm they pass**

Run:

```powershell
backend\mvnw.cmd -Dtest=SimpleTextChunkerTest,ManualTextIndexingAppServiceTest test
```

Expected:

- `BUILD SUCCESS`
- chunking and indexing tests both pass

- [ ] **Step 5: Commit the indexing slice**

```powershell
git add backend/pom.xml `
        .env.example `
        backend/src/main/resources/application.yml `
        backend/src/main/resources/db/migration/V2__manual_text_retrieval.sql `
        backend/src/main/java/org/xhy/raglearn/common/exception/BusinessException.java `
        backend/src/main/java/org/xhy/raglearn/common/exception/GlobalExceptionHandler.java `
        backend/src/main/java/org/xhy/raglearn/domain/retrieval `
        backend/src/main/java/org/xhy/raglearn/application/retrieval `
        backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval `
        backend/src/test/java/org/xhy/raglearn/application/retrieval/ManualTextIndexingAppServiceTest.java
git commit -m "feat(rag-learn): add manual text indexing flow"
```

### Task 3: Search Flow, LangChain4j Gateway, and HTTP Endpoints

**Files:**
- Create: `backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ChunkSearchMatch.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchCommand.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchHit.java`
- Create: `backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchResult.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingProperties.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingConfig.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingModelFactory.java`
- Create: `backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/LangChain4jManualTextVectorGateway.java`
- Create: `backend/src/main/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalController.java`
- Modify: `backend/src/main/java/org/xhy/raglearn/application/retrieval/ManualTextRetrievalAppService.java`
- Create: `backend/src/test/java/org/xhy/raglearn/application/retrieval/ManualTextSearchAppServiceTest.java`
- Create: `backend/src/test/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalControllerTest.java`

- [ ] **Step 1: Write the failing search tests**

`backend/src/test/java/org/xhy/raglearn/application/retrieval/ManualTextSearchAppServiceTest.java`

```java
package org.xhy.raglearn.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchResult;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

class ManualTextSearchAppServiceTest {

    @Test
    void searches_within_the_current_experiment_only() {
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                new SearchExperimentRepository(true),
                new SearchChunkRepository(),
                new SearchOnlyVectorGateway(),
                new SimpleTextChunker()
        );

        ManualTextSearchResult result = service.searchManualText(new ManualTextSearchCommand(7L, "What stores vectors?", 3));

        assertThat(result.experimentId()).isEqualTo(7L);
        assertThat(result.results()).hasSize(1);
        assertThat(result.results().get(0).content()).contains("pgvector");
    }

    @Test
    void rejects_missing_experiment() {
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                new SearchExperimentRepository(false),
                new SearchChunkRepository(),
                new SearchOnlyVectorGateway(),
                new SimpleTextChunker()
        );

        assertThatThrownBy(() -> service.searchManualText(new ManualTextSearchCommand(99L, "question", 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("experiment");
    }

    private static final class SearchOnlyVectorGateway implements ManualTextVectorGateway {
        @Override
        public void storeChunk(org.xhy.raglearn.domain.retrieval.model.ManualTextChunk chunk) {
        }

        @Override
        public List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
            return List.of(new ChunkSearchMatch(0.91, 11L, 1, "pgvector stores embeddings inside PostgreSQL."));
        }
    }

    private static final class SearchExperimentRepository implements ManualTextExperimentRepository {
        private final boolean present;

        private SearchExperimentRepository(boolean present) {
            this.present = present;
        }

        @Override
        public ManualTextExperiment create(String title, String rawText) {
            throw new UnsupportedOperationException("create is not used in search tests");
        }

        @Override
        public void updateChunkCount(long experimentId, int chunkCount) {
            throw new UnsupportedOperationException("updateChunkCount is not used in search tests");
        }

        @Override
        public Optional<ManualTextExperiment> findById(long experimentId) {
            if (!present) {
                return Optional.empty();
            }
            return Optional.of(new ManualTextExperiment(experimentId, "RAG", "raw", 2));
        }
    }

    private static final class SearchChunkRepository implements ManualTextChunkRepository {
        @Override
        public List<org.xhy.raglearn.domain.retrieval.model.ManualTextChunk> saveAll(
                long experimentId,
                List<org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft> drafts
        ) {
            throw new UnsupportedOperationException("saveAll is not used in search tests");
        }

        @Override
        public List<org.xhy.raglearn.domain.retrieval.model.ManualTextChunk> findByExperimentId(long experimentId) {
            return List.of();
        }
    }
}
```

`backend/src/test/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalControllerTest.java`

```java
package org.xhy.raglearn.interfaces.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.xhy.raglearn.application.retrieval.ManualTextRetrievalAppService;
import org.xhy.raglearn.application.retrieval.dto.ManualTextChunkView;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexResult;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchHit;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchResult;

@WebMvcTest(ManualTextRetrievalController.class)
class ManualTextRetrievalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ManualTextRetrievalAppService manualTextRetrievalAppService;

    @Test
    void rejects_blank_raw_text() throws Exception {
        mockMvc.perform(post("/v1/retrieval/manual-text/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Blank\",\"rawText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void returns_search_results() throws Exception {
        when(manualTextRetrievalAppService.searchManualText(any())).thenReturn(
                new ManualTextSearchResult(
                        7L,
                        "What stores vectors?",
                        3,
                        List.of(new ManualTextSearchHit(0.91, 11L, 1, "pgvector stores embeddings inside PostgreSQL."))
                )
        );

        mockMvc.perform(post("/v1/retrieval/manual-text/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"experimentId\":7,\"question\":\"What stores vectors?\",\"topK\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.results[0].chunkIndex").value(1));
    }
}
```

- [ ] **Step 2: Run the search tests and confirm they fail**

Run:

```powershell
backend\mvnw.cmd -Dtest=ManualTextSearchAppServiceTest,ManualTextRetrievalControllerTest test
```

Expected:

- build fails because search DTOs, controller, and vector gateway implementation are not complete yet

- [ ] **Step 3: Implement the search backend and HTTP layer**

Create search DTOs:

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchCommand.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualTextSearchCommand(
        @NotNull Long experimentId,
        @NotBlank String question,
        @Min(1) Integer topK
) {
    public int normalizedTopK() {
        return topK == null ? 3 : topK;
    }
}
```

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchHit.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

public record ManualTextSearchHit(double score, Long chunkId, int chunkIndex, String content) {
}
```

`backend/src/main/java/org/xhy/raglearn/application/retrieval/dto/ManualTextSearchResult.java`

```java
package org.xhy.raglearn.application.retrieval.dto;

import java.util.List;

public record ManualTextSearchResult(Long experimentId, String question, int topK, List<ManualTextSearchHit> results) {
}
```

Create embedding config:

`backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingProperties.java`

```java
package org.xhy.raglearn.infrastructure.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "embedding")
public class EmbeddingProperties {

    private String apiKey;
    private String baseUrl;
    private String model;
    private int dimension = 1024;
    private String vectorStoreTable = "public.vector_store";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public int getDimension() { return dimension; }
    public void setDimension(int dimension) { this.dimension = dimension; }
    public String getVectorStoreTable() { return vectorStoreTable; }
    public void setVectorStoreTable(String vectorStoreTable) { this.vectorStoreTable = vectorStoreTable; }
}
```

`backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingConfig.java`

```java
package org.xhy.raglearn.infrastructure.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    private final EmbeddingProperties properties;

    public EmbeddingConfig(EmbeddingProperties properties) {
        this.properties = properties;
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${DB_HOST:localhost}") String host,
            @Value("${DB_PORT:5432}") int port,
            @Value("${DB_NAME:agentx_rag_learn}") String database,
            @Value("${DB_USER:postgres}") String user,
            @Value("${DB_PASSWORD:postgres}") String password
    ) {
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table(properties.getVectorStoreTable())
                .dimension(properties.getDimension())
                .metadataStorageConfig(MetadataStorageConfig.combinedJsonb())
                .createTable(true)
                .build();
    }
}
```

`backend/src/main/java/org/xhy/raglearn/infrastructure/embedding/EmbeddingModelFactory.java`

```java
package org.xhy.raglearn.infrastructure.embedding;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xhy.raglearn.common.exception.BusinessException;

@Component
public class EmbeddingModelFactory {

    private final EmbeddingProperties properties;

    public EmbeddingModelFactory(EmbeddingProperties properties) {
        this.properties = properties;
    }

    public OpenAiEmbeddingModel create() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new BusinessException("Embedding API key is missing.");
        }
        return OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .build();
    }
}
```

Create `backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/LangChain4jManualTextVectorGateway.java`:

```java
package org.xhy.raglearn.infrastructure.retrieval;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.Embedding;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import org.springframework.stereotype.Component;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingModelFactory;

@Component
public class LangChain4jManualTextVectorGateway implements ManualTextVectorGateway {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModelFactory embeddingModelFactory;

    public LangChain4jManualTextVectorGateway(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModelFactory embeddingModelFactory
    ) {
        this.embeddingStore = embeddingStore;
        this.embeddingModelFactory = embeddingModelFactory;
    }

    @Override
    public void storeChunk(ManualTextChunk chunk) {
        OpenAiEmbeddingModel model = embeddingModelFactory.create();
        TextSegment segment = TextSegment.from(
                chunk.content(),
                dev.langchain4j.data.document.Metadata.from(
                        "experimentId", chunk.experimentId().toString(),
                        "chunkId", chunk.id().toString(),
                        "chunkIndex", Integer.toString(chunk.chunkIndex())
                )
        );
        Embedding embedding = model.embed(chunk.content()).content();
        embeddingStore.add(embedding, segment);
    }

    @Override
    public List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
        OpenAiEmbeddingModel model = embeddingModelFactory.create();
        Embedding queryEmbedding = model.embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(topK)
                        .filter(metadataKey("experimentId").isEqualTo(Long.toString(experimentId)))
                        .build()
        ).matches();

        return matches.stream()
                .map(match -> new ChunkSearchMatch(
                        match.score(),
                        Long.valueOf(match.embedded().metadata().getString("chunkId")),
                        Integer.parseInt(match.embedded().metadata().getString("chunkIndex")),
                        match.embedded().text()
                ))
                .toList();
    }
}
```

Update `backend/src/main/java/org/xhy/raglearn/application/retrieval/ManualTextRetrievalAppService.java` to add search:

```java
public ManualTextSearchResult searchManualText(ManualTextSearchCommand command) {
    var experiment = experimentRepository.findById(command.experimentId())
            .orElseThrow(() -> new BusinessException("Manual text experiment does not exist."));

    if (!StringUtils.hasText(command.question())) {
        throw new BusinessException("Manual text question must not be blank.");
    }

    return new ManualTextSearchResult(
            experiment.id(),
            command.question(),
            command.normalizedTopK(),
            vectorGateway.search(experiment.id(), command.question(), command.normalizedTopK()).stream()
                    .map(match -> new ManualTextSearchHit(
                            match.score(),
                            match.chunkId(),
                            match.chunkIndex(),
                            match.content()
                    ))
                    .toList()
    );
}
```

Create controller `backend/src/main/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalController.java`:

```java
package org.xhy.raglearn.interfaces.http;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.raglearn.application.retrieval.ManualTextRetrievalAppService;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchCommand;
import org.xhy.raglearn.common.api.ApiResponse;

@RestController
@RequestMapping("/v1/retrieval/manual-text")
public class ManualTextRetrievalController {

    private final ManualTextRetrievalAppService manualTextRetrievalAppService;

    public ManualTextRetrievalController(ManualTextRetrievalAppService manualTextRetrievalAppService) {
        this.manualTextRetrievalAppService = manualTextRetrievalAppService;
    }

    @PostMapping("/index")
    public ApiResponse<?> index(@Valid @RequestBody ManualTextIndexCommand command) {
        return ApiResponse.success(manualTextRetrievalAppService.indexManualText(command));
    }

    @PostMapping("/search")
    public ApiResponse<?> search(@Valid @RequestBody ManualTextSearchCommand command) {
        return ApiResponse.success(manualTextRetrievalAppService.searchManualText(command));
    }
}
```

- [ ] **Step 4: Run backend tests and confirm search plus controller pass**

Run:

```powershell
backend\mvnw.cmd -Dtest=SimpleTextChunkerTest,ManualTextIndexingAppServiceTest,ManualTextSearchAppServiceTest,ManualTextRetrievalControllerTest test
```

Expected:

- `BUILD SUCCESS`
- search and HTTP validation coverage pass

- [ ] **Step 5: Commit the retrieval backend**

```powershell
git add backend/src/main/java/org/xhy/raglearn/infrastructure/embedding `
        backend/src/main/java/org/xhy/raglearn/infrastructure/retrieval/LangChain4jManualTextVectorGateway.java `
        backend/src/main/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalController.java `
        backend/src/main/java/org/xhy/raglearn/application/retrieval `
        backend/src/main/java/org/xhy/raglearn/domain/retrieval/model/ChunkSearchMatch.java `
        backend/src/test/java/org/xhy/raglearn/application/retrieval/ManualTextSearchAppServiceTest.java `
        backend/src/test/java/org/xhy/raglearn/interfaces/http/ManualTextRetrievalControllerTest.java
git commit -m "feat(rag-learn): add manual text retrieval search flow"
```

### Task 4: Retrieval Workbench Frontend

**Files:**
- Modify: `frontend/app/retrieval/page.tsx`
- Modify: `frontend/app/globals.css`
- Create: `frontend/components/manual-text-retrieval-workbench.tsx`
- Create: `frontend/lib/manual-text-retrieval.ts`

- [ ] **Step 1: Make the retrieval page depend on the new workbench and confirm the build fails**

Replace `frontend/app/retrieval/page.tsx` with:

```tsx
import { ManualTextRetrievalWorkbench } from "@/components/manual-text-retrieval-workbench";
import { ShellLayout } from "@/components/shell-layout";

export default function RetrievalPage() {
  return (
    <ShellLayout
      title="检索实验台"
      description="这一页现在先专注于最小 RAG 闭环：手工输入原始文本，观察 chunk，验证同一实验内的向量检索。"
    >
      <ManualTextRetrievalWorkbench />
    </ShellLayout>
  );
}
```

- [ ] **Step 2: Run the frontend build and confirm it fails**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- build fails because `manual-text-retrieval-workbench.tsx` and its API helper do not exist yet

- [ ] **Step 3: Implement the workbench UI and API client**

Create `frontend/lib/manual-text-retrieval.ts`:

```ts
export type ApiEnvelope<T> = {
  success: boolean;
  code: string;
  message: string;
  data: T | null;
};

export type ManualTextChunkView = {
  chunkId: number;
  chunkIndex: number;
  content: string;
};

export type ManualTextIndexResult = {
  experimentId: number;
  title: string | null;
  rawText: string;
  chunkCount: number;
  chunks: ManualTextChunkView[];
};

export type ManualTextSearchHit = {
  score: number;
  chunkId: number;
  chunkIndex: number;
  content: string;
};

export type ManualTextSearchResult = {
  experimentId: number;
  question: string;
  topK: number;
  results: ManualTextSearchHit[];
};

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8181/api/v1";

async function postJson<T>(path: string, body: unknown): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(body)
  });

  const payload = (await response.json()) as ApiEnvelope<T>;
  if (!response.ok || !payload.success || !payload.data) {
    throw new Error(payload.message || "Request failed.");
  }
  return payload.data;
}

export function indexManualText(input: {
  title: string;
  rawText: string;
}) {
  return postJson<ManualTextIndexResult>("/retrieval/manual-text/index", input);
}

export function searchManualText(input: {
  experimentId: number;
  question: string;
  topK: number;
}) {
  return postJson<ManualTextSearchResult>("/retrieval/manual-text/search", input);
}
```

Create `frontend/components/manual-text-retrieval-workbench.tsx`:

```tsx
"use client";

import { FormEvent, useState } from "react";
import {
  indexManualText,
  ManualTextIndexResult,
  ManualTextSearchResult,
  searchManualText
} from "@/lib/manual-text-retrieval";

export function ManualTextRetrievalWorkbench() {
  const [title, setTitle] = useState("RAG 最小闭环实验");
  const [rawText, setRawText] = useState(
    "Spring Boot 用来快速构建 Java 服务。\n\npgvector 可以把向量存到 PostgreSQL 中。"
  );
  const [question, setQuestion] = useState("哪个组件负责把向量存到 PostgreSQL?");
  const [topK, setTopK] = useState(3);
  const [indexResult, setIndexResult] = useState<ManualTextIndexResult | null>(null);
  const [searchResult, setSearchResult] = useState<ManualTextSearchResult | null>(null);
  const [indexing, setIndexing] = useState(false);
  const [searching, setSearching] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleIndex(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSearchResult(null);
    setIndexing(true);
    try {
      const result = await indexManualText({ title, rawText });
      setIndexResult(result);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "入库失败。");
    } finally {
      setIndexing(false);
    }
  }

  async function handleSearch(event: FormEvent) {
    event.preventDefault();
    if (!indexResult) {
      setError("请先完成当前文本的入库，再发起检索。");
      return;
    }

    setError(null);
    setSearching(true);
    try {
      const result = await searchManualText({
        experimentId: indexResult.experimentId,
        question,
        topK
      });
      setSearchResult(result);
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : "检索失败。");
    } finally {
      setSearching(false);
    }
  }

  return (
    <section className="shell-panel retrieval-workbench">
      <div className="workbench-grid">
        <form className="workbench-card" onSubmit={handleIndex}>
          <div className="brand-kicker">Step 1</div>
          <h3 className="section-title">手工文本入库</h3>
          <label className="field-label">
            标题
            <input value={title} onChange={(event) => setTitle(event.target.value)} className="text-input" />
          </label>
          <label className="field-label">
            原始文本
            <textarea
              value={rawText}
              onChange={(event) => setRawText(event.target.value)}
              className="text-area"
              rows={12}
            />
          </label>
          <button className="action-button" disabled={indexing}>
            {indexing ? "入库中..." : "切分并写入向量库"}
          </button>
        </form>

        <div className="workbench-card">
          <div className="brand-kicker">Step 2</div>
          <h3 className="section-title">Chunk 观察区</h3>
          <p className="section-copy">
            当前实验：{indexResult ? `#${indexResult.experimentId}` : "还没有完成入库"}
          </p>
          <div className="chunk-list">
            {(indexResult?.chunks ?? []).map((chunk) => (
              <article key={chunk.chunkId} className="chunk-card">
                <div className="module-phase">Chunk {chunk.chunkIndex}</div>
                <div className="module-copy">{chunk.content}</div>
              </article>
            ))}
          </div>
        </div>
      </div>

      <form className="workbench-card" onSubmit={handleSearch}>
        <div className="brand-kicker">Step 3</div>
        <h3 className="section-title">同实验检索</h3>
        <div className="search-grid">
          <label className="field-label">
            问题
            <input value={question} onChange={(event) => setQuestion(event.target.value)} className="text-input" />
          </label>
          <label className="field-label narrow-field">
            TopK
            <input
              type="number"
              min={1}
              max={10}
              value={topK}
              onChange={(event) => setTopK(Number(event.target.value))}
              className="text-input"
            />
          </label>
        </div>
        <button className="action-button" disabled={searching}>
          {searching ? "检索中..." : "执行向量检索"}
        </button>

        {error ? <div className="error-banner">{error}</div> : null}

        <div className="result-list">
          {(searchResult?.results ?? []).map((result) => (
            <article key={`${result.chunkId}-${result.chunkIndex}`} className="chunk-card">
              <div className="result-header">
                <span className="module-phase">Chunk {result.chunkIndex}</span>
                <span className="result-score">score: {result.score.toFixed(3)}</span>
              </div>
              <div className="module-copy">{result.content}</div>
            </article>
          ))}
        </div>
      </form>
    </section>
  );
}
```

Append styles to `frontend/app/globals.css`:

```css
.retrieval-workbench,
.workbench-grid,
.search-grid,
.chunk-list,
.result-list {
  display: grid;
  gap: 20px;
}

.workbench-grid {
  grid-template-columns: 1.1fr 0.9fr;
}

.workbench-card {
  border: 1px solid var(--line);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.62);
  padding: 20px;
}

.field-label {
  display: grid;
  gap: 8px;
  color: var(--muted);
  font-size: 14px;
}

.text-input,
.text-area {
  width: 100%;
  border: 1px solid var(--line);
  border-radius: 16px;
  padding: 12px 14px;
  font: inherit;
  background: rgba(255, 255, 255, 0.76);
  color: var(--ink);
}

.text-area {
  resize: vertical;
}

.action-button {
  border: none;
  border-radius: 999px;
  padding: 12px 18px;
  background: var(--accent);
  color: white;
  font: inherit;
  font-weight: 700;
  cursor: pointer;
}

.action-button:disabled {
  opacity: 0.72;
  cursor: wait;
}

.chunk-card {
  border: 1px solid var(--line);
  border-radius: 20px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.7);
}

.result-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.result-score {
  color: var(--muted);
  font-size: 13px;
}

.error-banner {
  border-left: 4px solid #b3261e;
  padding-left: 14px;
  color: #8a1c17;
}

.narrow-field {
  max-width: 120px;
}

@media (max-width: 960px) {
  .workbench-grid,
  .search-grid {
    grid-template-columns: 1fr;
  }

  .narrow-field {
    max-width: none;
  }
}
```

- [ ] **Step 4: Run the frontend build and confirm it passes**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- Next.js build succeeds with no missing-module or type errors

- [ ] **Step 5: Commit the retrieval workbench**

```powershell
git add frontend/app/retrieval/page.tsx `
        frontend/app/globals.css `
        frontend/components/manual-text-retrieval-workbench.tsx `
        frontend/lib/manual-text-retrieval.ts
git commit -m "feat(rag-learn): add manual text retrieval workbench"
```

### Task 5: End-to-End Verification and Branch Artifacts

**Files:**
- Create: `task-cards/learn-01-basic-rag-text.md`
- Create: `review-notes/learn-01-basic-rag-text.md`

- [ ] **Step 1: Run backend tests as a final gate**

Run:

```powershell
backend\mvnw.cmd -Dtest=SimpleTextChunkerTest,ManualTextIndexingAppServiceTest,ManualTextSearchAppServiceTest,ManualTextRetrievalControllerTest test
```

Expected:

- `BUILD SUCCESS`

- [ ] **Step 2: Run the backend package build**

Run:

```powershell
backend\mvnw.cmd -DskipTests package
```

Expected:

- jar packaging succeeds

- [ ] **Step 3: Run the frontend build gate**

Run:

```powershell
cd frontend
npm run build
```

Expected:

- frontend production build succeeds

- [ ] **Step 4: Run the manual walkthrough with a fixed sample**

Start services:

```powershell
cd infra
docker compose up -d
cd ..\backend
mvn spring-boot:run
cd ..\frontend
npm run dev
```

Use this raw text:

```text
Spring Boot 用来快速构建 Java 服务。

pgvector 可以把向量存进 PostgreSQL，并支持相似度检索。

Qwen3-Embedding-0.6B 可以把文本转换成 1024 维向量。
```

Use this question:

```text
哪个组件负责把向量存到 PostgreSQL？
```

Expected:

- indexing returns an `experimentId`
- chunk cards render on the page
- search uses the current `experimentId` automatically
- topK results include the `pgvector` chunk near the top

- [ ] **Step 5: Write branch docs and commit**

Create `task-cards/learn-01-basic-rag-text.md`:

```md
# learn/01-basic-rag-text

## AgentX 对照卡

本次功能：
手工文本 -> 切分 -> embedding -> pgvector -> 检索

AgentX 参考点：
- EmbeddingConfig
- EmbeddingModelFactory
- PgVectorEmbeddingStore
- TextSegment metadata 检索链路

这次学习版先简化掉什么：
- 文件上传
- dataset / file_detail / document_unit
- 问答生成
- 检索增强

## 任务卡

本次目标：
把最小 RAG 检索闭环真实跑通

本次只做：
- 手工输入文本
- chunk 列表展示
- 向量检索

本次不做：
- 文件上传
- QA
- 混合检索

输入：
- 原始文本
- 用户问题

输出：
- 切分后的 chunk
- topK 检索结果

完成标准：
- 问题能命中最相关 chunk

验证方式：
- 页面入库
- 页面检索
- 检查数据库记录
```

Create `review-notes/learn-01-basic-rag-text.md`:

```md
# learn/01-basic-rag-text Review

## Validation

- 后端测试通过
- 后端打包通过
- 前端构建通过
- 页面手工走通一次最小检索链路

## Review Checklist

1. 入口在哪：
   - `POST /api/v1/retrieval/manual-text/index`
   - `POST /api/v1/retrieval/manual-text/search`
   - `frontend/app/retrieval/page.tsx`

2. 主流程是什么：
   - 手工文本入库
   - chunk 保存
   - embedding 写入 `vector_store`
   - 问题检索

3. 数据从哪来，到哪去：
   - 原始文本 -> `manual_text_experiment`
   - chunk -> `manual_text_chunk`
   - embedding -> `vector_store`

4. 失败会发生什么：
   - 空文本报错
   - 空问题报错
   - 缺 embedding 配置报错

5. 一周后还能不能看懂：
   - 代码按 chunking / indexing / search / page 分开
   - 注释保留教学意图
```

Commit:

```powershell
git add task-cards/learn-01-basic-rag-text.md review-notes/learn-01-basic-rag-text.md
git commit -m "docs(rag-learn): add learn-01 task and review notes"
```
