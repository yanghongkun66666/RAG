package org.xhy.raglearn.infrastructure.embedding;

import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingModelFactory {

    private final EmbeddingProperties properties;

    public EmbeddingModelFactory(EmbeddingProperties properties) {
        this.properties = properties;
    }

    public OpenAiEmbeddingModel create() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .modelName(properties.getModel())
                .dimensions(properties.getDimension())
                .build();
    }
}
