package org.xhy.raglearn.application.retrieval.dto;

public record ManualTextSearchHit(double score, Long chunkId, int chunkIndex, String content) {
}
