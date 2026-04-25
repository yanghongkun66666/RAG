package org.xhy.raglearn.application.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchHit;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchResult;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

class ManualTextSearchAppServiceTest {

    @Test
    void searches_manual_text_within_the_requested_experiment() {
        InMemoryExperimentRepository experimentRepository = new InMemoryExperimentRepository();
        RecordingVectorGateway vectorGateway = new RecordingVectorGateway();
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                experimentRepository,
                new InMemoryChunkRepository(),
                vectorGateway,
                new SimpleTextChunker(200, 20)
        );

        ManualTextExperiment experiment = experimentRepository.create(
                "RAG Intro",
                "Spring Boot builds services.\n\npgvector stores embeddings."
        );
        vectorGateway.matches = List.of(
                new ChunkSearchMatch(0.93, 101L, 1, "pgvector stores embeddings.")
        );

        ManualTextSearchResult result = service.searchManualText(new ManualTextSearchCommand(
                experiment.id(),
                "Which component stores embeddings?",
                3
        ));

        assertThat(vectorGateway.lastExperimentId).isEqualTo(experiment.id());
        assertThat(vectorGateway.lastQuestion).isEqualTo("Which component stores embeddings?");
        assertThat(vectorGateway.lastTopK).isEqualTo(3);
        assertThat(result).isEqualTo(new ManualTextSearchResult(
                experiment.id(),
                "Which component stores embeddings?",
                3,
                List.of(new ManualTextSearchHit(0.93, 101L, 1, "pgvector stores embeddings."))
        ));
    }

    @Test
    void rejects_blank_question() {
        InMemoryExperimentRepository experimentRepository = new InMemoryExperimentRepository();
        experimentRepository.create("RAG Intro", "Spring Boot builds services.");
        ManualTextRetrievalAppService service = new ManualTextRetrievalAppService(
                experimentRepository,
                new InMemoryChunkRepository(),
                new RecordingVectorGateway(),
                new SimpleTextChunker(200, 20)
        );

        assertThatThrownBy(() -> service.searchManualText(new ManualTextSearchCommand(1L, "   ", 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("question");
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

        @Override
        public List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts) {
            return List.of();
        }

        @Override
        public List<ManualTextChunk> findByExperimentId(long experimentId) {
            return List.of();
        }
    }

    private static final class RecordingVectorGateway implements ManualTextVectorGateway {
        private long lastExperimentId;
        private String lastQuestion;
        private int lastTopK;
        private List<ChunkSearchMatch> matches = new ArrayList<>();

        @Override
        public void storeChunk(ManualTextChunk chunk) {
        }

        @Override
        public List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
            this.lastExperimentId = experimentId;
            this.lastQuestion = question;
            this.lastTopK = topK;
            return matches;
        }
    }
}
