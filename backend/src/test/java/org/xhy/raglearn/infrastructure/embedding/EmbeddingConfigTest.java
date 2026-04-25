package org.xhy.raglearn.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.xhy.raglearn.common.exception.BusinessException;

class EmbeddingConfigTest {

    private final EmbeddingConfig embeddingConfig = new EmbeddingConfig();

    @Test
    void fails_fast_when_vector_store_table_is_not_supported() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(1024);

        assertThatThrownBy(() -> embeddingConfig.embeddingStore(
                new DriverManagerDataSource(),
                properties,
                "custom.vector_store"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("public.vector_store");
    }

    @Test
    void fails_fast_when_embedding_dimension_is_not_supported() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setDimension(768);

        assertThatThrownBy(() -> embeddingConfig.embeddingStore(
                new DriverManagerDataSource(),
                properties,
                "public.vector_store"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1024");
    }
}
