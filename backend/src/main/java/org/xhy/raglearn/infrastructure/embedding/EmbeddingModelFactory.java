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
            throw new BusinessException("embedding api key must not be blank");
        }

        return OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
    }
}
