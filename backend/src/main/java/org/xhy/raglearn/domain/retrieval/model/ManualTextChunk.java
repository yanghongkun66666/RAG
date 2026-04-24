package org.xhy.raglearn.domain.retrieval.model;

public record ManualTextChunk(Long id, Long experimentId, int chunkIndex, String content) {
}
