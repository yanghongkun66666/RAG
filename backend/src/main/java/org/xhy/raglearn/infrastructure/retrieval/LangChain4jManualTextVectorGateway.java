package org.xhy.raglearn.infrastructure.retrieval;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.List;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
        Metadata metadata = Metadata.from("experimentId", Long.toString(chunk.experimentId()))
                .put("chunkId", chunk.id())
                .put("chunkIndex", chunk.chunkIndex());
        TextSegment segment = TextSegment.from(chunk.content(), metadata);
        Embedding embedding = embeddingModelFactory.create().embed(chunk.content()).content();
        embeddingStore.add(embedding, segment);
    }

    @Override
    public List<ChunkSearchMatch> search(long experimentId, String question, int topK) {
        Embedding queryEmbedding = embeddingModelFactory.create().embed(question).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .filter(metadataKey("experimentId").isEqualTo(Long.toString(experimentId)))
                .build())
                .matches();

        return matches.stream()
                .map(match -> new ChunkSearchMatch(
                        match.score(),
                        match.embedded().metadata().getLong("chunkId"),
                        match.embedded().metadata().getInteger("chunkIndex"),
                        match.embedded().text()
                ))
                .toList();
    }
}
