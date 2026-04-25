package org.xhy.raglearn.domain.retrieval.repository;

import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;

import java.util.Optional;

public interface ManualTextExperimentRepository {

    ManualTextExperiment create(String title, String rawText);

    void updateChunkCount(long experimentId, int chunkCount);

    Optional<ManualTextExperiment> findById(long experimentId);
}
