package org.xhy.raglearn.infrastructure.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglearn.embedding")
public class EmbeddingProperties {

    private String apiKey = "";
    private String baseUrl = "https://api.siliconflow.cn/v1";
    private String model = "Qwen/Qwen3-Embedding-0.6B";
    private Integer dimension = 1024;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }
}
