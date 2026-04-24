package org.xhy.raglearn.domain.retrieval.gateway;

import org.xhy.raglearn.domain.retrieval.model.ChunkSearchMatch;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;

import java.util.List;

public interface ManualTextVectorGateway {

    void storeChunk(ManualTextChunk chunk);

    List<ChunkSearchMatch> search(long experimentId, String question, int topK);
}
