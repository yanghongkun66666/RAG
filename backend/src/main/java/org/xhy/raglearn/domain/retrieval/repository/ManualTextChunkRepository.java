package org.xhy.raglearn.domain.retrieval.repository;

import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;

import java.util.List;

public interface ManualTextChunkRepository {

    List<ManualTextChunk> saveAll(long experimentId, List<ManualTextChunkDraft> drafts);

    List<ManualTextChunk> findByExperimentId(long experimentId);
}
