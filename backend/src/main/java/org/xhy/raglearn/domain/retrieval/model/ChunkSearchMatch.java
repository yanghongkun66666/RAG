package org.xhy.raglearn.domain.retrieval.model;

public record ChunkSearchMatch(double score, Long chunkId, int chunkIndex, String content) {
}
