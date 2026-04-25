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
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EmbeddingProperties.class)
public class EmbeddingConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            DataSource dataSource,
            EmbeddingProperties embeddingProperties,
            @Value("${raglearn.vector-store.table}") String vectorStoreTable
    ) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(vectorStoreTable)
                .dimension(embeddingProperties.getDimension())
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
}
