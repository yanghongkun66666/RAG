package org.xhy.raglearn.domain.retrieval.repository;

import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;

public interface ManualTextExperimentRepository {

    ManualTextExperiment create(String title, String rawText);

    void updateChunkCount(long experimentId, int chunkCount);

    ManualTextExperiment findById(long experimentId);
}
