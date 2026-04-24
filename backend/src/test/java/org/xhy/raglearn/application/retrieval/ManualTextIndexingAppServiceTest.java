package org.xhy.raglearn.application.retrieval;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.stereotype.Service;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManualTextIndexingAppServiceTest {

    @Mock
    private ManualTextExperimentRepository experimentRepository;

    @Mock
    private ManualTextChunkRepository chunkRepository;

    @Mock
    private ManualTextVectorGateway vectorGateway;

    @Mock
    private SimpleTextChunker chunker;

    @InjectMocks
    private ManualTextRetrievalAppService appService;

    @Test
    void staysAsPlainTaskTwoClassWithoutSpringServiceRegistration() {
        assertFalse(ManualTextRetrievalAppService.class.isAnnotationPresent(Service.class));
    }

    @Test
    void indexesManualTextAndReturnsPersistedChunks() {
        ManualTextIndexCommand command = new ManualTextIndexCommand(
                "Intro Notes",
                "alpha beta\n\ngamma delta"
        );
        ManualTextExperiment createdExperiment = new ManualTextExperiment(11L, "Intro Notes", command.rawText(), 0);
        List<ManualTextChunkDraft> drafts = List.of(
                new ManualTextChunkDraft(0, "alpha beta"),
                new ManualTextChunkDraft(1, "gamma delta")
        );
        List<ManualTextChunk> persistedChunks = List.of(
                new ManualTextChunk(101L, 11L, 0, "alpha beta"),
                new ManualTextChunk(102L, 11L, 1, "gamma delta")
        );

        when(experimentRepository.create("Intro Notes", command.rawText())).thenReturn(createdExperiment);
        when(chunker.chunk(command.rawText())).thenReturn(drafts);
        when(chunkRepository.saveAll(11L, drafts)).thenReturn(persistedChunks);
        when(experimentRepository.findById(11L)).thenReturn(Optional.of(
                new ManualTextExperiment(11L, "Intro Notes", command.rawText(), 2)
        ));

        ManualTextIndexResult result = appService.indexManualText(command);

        assertEquals(new ManualTextIndexResult(
                11L,
                "Intro Notes",
                command.rawText(),
                2,
                List.of(
                        new ManualTextChunkView(101L, 0, "alpha beta"),
                        new ManualTextChunkView(102L, 1, "gamma delta")
                )
        ), result);
        verify(experimentRepository).create("Intro Notes", command.rawText());
        verify(chunker).chunk(command.rawText());
        verify(chunkRepository).saveAll(11L, drafts);
        verify(vectorGateway).storeChunk(persistedChunks.get(0));
        verify(vectorGateway).storeChunk(persistedChunks.get(1));
        verify(experimentRepository).updateChunkCount(11L, 2);
        verify(experimentRepository).findById(11L);
    }

    @Test
    void rejectsBlankRawTextBeforePersisting() {
        ManualTextIndexCommand command = new ManualTextIndexCommand("Intro Notes", "   ");

        BusinessException exception = assertThrows(BusinessException.class, () -> appService.indexManualText(command));

        assertEquals("rawText must not be blank", exception.getMessage());
        verifyNoInteractions(experimentRepository, chunkRepository, vectorGateway, chunker);
    }
}
