package org.xhy.raglearn.application.retrieval.dto;

import java.util.List;

public record ManualTextSearchResult(
        Long experimentId,
        String question,
        int topK,
        List<ManualTextSearchHit> results
) {
}
