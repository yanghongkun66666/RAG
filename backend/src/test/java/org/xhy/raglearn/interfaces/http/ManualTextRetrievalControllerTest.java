package org.xhy.raglearn.interfaces.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.xhy.raglearn.application.retrieval.ManualTextRetrievalAppService;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchHit;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchResult;
import org.xhy.raglearn.common.exception.GlobalExceptionHandler;

@WebMvcTest(ManualTextRetrievalController.class)
@Import(GlobalExceptionHandler.class)
class ManualTextRetrievalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ManualTextRetrievalAppService manualTextRetrievalAppService;

    @Test
    void rejects_blank_raw_text_on_index_request() throws Exception {
        mockMvc.perform(post("/v1/retrieval/manual-text/index")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Intro Notes",
                                  "rawText": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(manualTextRetrievalAppService);
    }

    @Test
    void returns_successful_search_response() throws Exception {
        ManualTextSearchResult result = new ManualTextSearchResult(
                11L,
                "Which component stores embeddings?",
                2,
                java.util.List.of(new ManualTextSearchHit(0.91, 102L, 1, "pgvector stores embeddings."))
        );
        given(manualTextRetrievalAppService.searchManualText(any(ManualTextSearchCommand.class))).willReturn(result);

        mockMvc.perform(post("/v1/retrieval/manual-text/search")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ManualTextSearchCommand(
                                11L,
                                "Which component stores embeddings?",
                                2
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.experimentId").value(11))
                .andExpect(jsonPath("$.data.question").value("Which component stores embeddings?"))
                .andExpect(jsonPath("$.data.topK").value(2))
                .andExpect(jsonPath("$.data.results[0].chunkId").value(102))
                .andExpect(jsonPath("$.data.results[0].chunkIndex").value(1))
                .andExpect(jsonPath("$.data.results[0].content").value("pgvector stores embeddings."));
    }
}
