package org.xhy.raglearn.application.retrieval.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualTextIndexCommand(String title, @NotBlank String rawText) {
}
