package org.xhy.raglearn.infrastructure.embedding;

import javax.sql.DataSource;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    private static final String SUPPORTED_VECTOR_STORE_TABLE = "public.vector_store";
    private static final int SUPPORTED_EMBEDDING_DIMENSION = 1024;

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            EmbeddingProperties embeddingProperties,
            @Value("${raglearn.vector-store.table}") String vectorStoreTable
    ) {
        validateSchemaContract(embeddingProperties, vectorStoreTable);

        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(vectorStoreTable)
                .dimension(embeddingProperties.getDimension())
                .createTable(false)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(DefaultMetadataStorageConfig.builder()
                        .storageMode(MetadataStorageMode.COMBINED_JSONB)
                        .columnDefinitions(List.of("metadata JSONB NULL"))
                        .build())
                .build();
    }

    @Bean
    public SimpleTextChunker simpleTextChunker() {
        return new SimpleTextChunker();
    }

    private void validateSchemaContract(EmbeddingProperties embeddingProperties, String vectorStoreTable) {
        if (!SUPPORTED_VECTOR_STORE_TABLE.equals(vectorStoreTable)) {
            throw new BusinessException(
                    "Task 3 supports only raglearn.vector-store.table=%s".formatted(SUPPORTED_VECTOR_STORE_TABLE)
            );
        }

        if (!Integer.valueOf(SUPPORTED_EMBEDDING_DIMENSION).equals(embeddingProperties.getDimension())) {
            throw new BusinessException(
                    "Task 3 supports only raglearn.embedding.dimension=%d".formatted(SUPPORTED_EMBEDDING_DIMENSION)
            );
        }
    }
}
