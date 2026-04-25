package org.xhy.raglearn.infrastructure.retrieval;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import java.util.List;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.infrastructure.embedding.EmbeddingModelFactory;

@Component
public class LangChain4jManualTextVectorGateway implements ManualTextVectorGateway {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModelFactory embeddingModelFactory;
    private final DataSource dataSource;
    private final String qualifiedVectorStoreTable;

    public LangChain4jManualTextVectorGateway(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModelFactory embeddingModelFactory,
            DataSource dataSource,
            @Value("${raglearn.vector-store.table}") String vectorStoreTable
    ) {
        this.embeddingStore = embeddingStore;
        this.embeddingModelFactory = embeddingModelFactory;
        this.dataSource = dataSource;
        this.qualifiedVectorStoreTable = toQualifiedTableName(vectorStoreTable);
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
    public void deleteByExperimentId(long experimentId) {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "DELETE FROM %s WHERE metadata->>'experimentId' = ?".formatted(qualifiedVectorStoreTable)
             )) {
            statement.setString(1, Long.toString(experimentId));
            statement.executeUpdate();
        } catch (Exception exception) {
            throw new BusinessException("Failed to clean up vector rows for experiment " + experimentId);
        }
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

    private static String toQualifiedTableName(String configuredTable) {
        if (configuredTable == null || configuredTable.isBlank()) {
            throw new BusinessException("raglearn.vector-store.table must not be blank");
        }

        String[] parts = configuredTable.split("\\.");
        if (parts.length == 0 || parts.length > 2) {
            throw new BusinessException("raglearn.vector-store.table must be table or schema.table");
        }

        for (String part : parts) {
            if (!SQL_IDENTIFIER.matcher(part).matches()) {
                throw new BusinessException("raglearn.vector-store.table contains an unsafe identifier");
            }
        }

        return String.join(".", parts);
    }
}
