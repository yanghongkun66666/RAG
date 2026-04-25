package org.xhy.raglearn.infrastructure.embedding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.xhy.raglearn.common.exception.BusinessException;

class EmbeddingModelFactoryTest {

    @Test
    void fails_fast_when_embedding_api_key_is_missing() {
        EmbeddingProperties properties = new EmbeddingProperties();
        properties.setApiKey("   ");
        EmbeddingModelFactory factory = new EmbeddingModelFactory(properties);

        assertThatThrownBy(factory::create)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("embedding api key");
    }
}
