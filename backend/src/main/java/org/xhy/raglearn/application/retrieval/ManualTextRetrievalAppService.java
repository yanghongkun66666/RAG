package org.xhy.raglearn.application.retrieval;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xhy.raglearn.application.retrieval.dto.ManualTextChunkView;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexResult;
import org.xhy.raglearn.common.exception.BusinessException;
import org.xhy.raglearn.domain.retrieval.gateway.ManualTextVectorGateway;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunk;
import org.xhy.raglearn.domain.retrieval.model.ManualTextChunkDraft;
import org.xhy.raglearn.domain.retrieval.model.ManualTextExperiment;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextChunkRepository;
import org.xhy.raglearn.domain.retrieval.repository.ManualTextExperimentRepository;
import org.xhy.raglearn.domain.retrieval.service.SimpleTextChunker;

import java.util.List;

@Service
public class ManualTextRetrievalAppService {

    private final ManualTextExperimentRepository experimentRepository;
    private final ManualTextChunkRepository chunkRepository;
    private final ManualTextVectorGateway vectorGateway;
    private final SimpleTextChunker chunker;

    public ManualTextRetrievalAppService(
            ManualTextExperimentRepository experimentRepository,
            ManualTextChunkRepository chunkRepository,
            ManualTextVectorGateway vectorGateway,
            SimpleTextChunker chunker
    ) {
        this.experimentRepository = experimentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorGateway = vectorGateway;
        this.chunker = chunker;
    }

    @Transactional
    public ManualTextIndexResult indexManualText(ManualTextIndexCommand command) {
        if (command.rawText() == null || command.rawText().isBlank()) {
            throw new BusinessException("rawText must not be blank");
        }

        ManualTextExperiment createdExperiment = experimentRepository.create(command.title(), command.rawText());
        List<ManualTextChunkDraft> drafts = chunker.chunk(command.rawText());
        List<ManualTextChunk> chunks = chunkRepository.saveAll(createdExperiment.id(), drafts);

        for (ManualTextChunk chunk : chunks) {
            vectorGateway.storeChunk(chunk);
        }

        experimentRepository.updateChunkCount(createdExperiment.id(), chunks.size());
        ManualTextExperiment experiment = experimentRepository.findById(createdExperiment.id());
        if (experiment == null) {
            experiment = new ManualTextExperiment(
                    createdExperiment.id(),
                    createdExperiment.title(),
                    createdExperiment.rawText(),
                    chunks.size()
            );
        }

        return new ManualTextIndexResult(
                experiment.id(),
                experiment.title(),
                experiment.rawText(),
                experiment.chunkCount(),
                chunks.stream()
                        .map(chunk -> new ManualTextChunkView(chunk.id(), chunk.chunkIndex(), chunk.content()))
                        .toList()
        );
    }
}
