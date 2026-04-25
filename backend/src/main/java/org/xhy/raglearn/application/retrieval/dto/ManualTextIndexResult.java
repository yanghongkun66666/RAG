package org.xhy.raglearn.application.retrieval.dto;

import java.util.List;

public record ManualTextIndexResult(
        Long experimentId,
        String title,
        String rawText,
        int chunkCount,
        List<ManualTextChunkView> chunks
) {
}
