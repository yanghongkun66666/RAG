package org.xhy.raglearn.application.retrieval.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ManualTextSearchCommand(
        @NotNull Long experimentId,
        @NotBlank String question,
        @Min(1) Integer topK
) {

    public int normalizedTopK() {
        return topK == null ? 3 : topK;
    }
}
