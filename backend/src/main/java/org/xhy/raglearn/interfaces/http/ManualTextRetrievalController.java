package org.xhy.raglearn.interfaces.http;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.raglearn.application.retrieval.ManualTextRetrievalAppService;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextIndexResult;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchCommand;
import org.xhy.raglearn.application.retrieval.dto.ManualTextSearchResult;
import org.xhy.raglearn.common.api.ApiResponse;

@RestController
@RequestMapping("/v1/retrieval/manual-text")
public class ManualTextRetrievalController {

    private final ManualTextRetrievalAppService manualTextRetrievalAppService;

    public ManualTextRetrievalController(ManualTextRetrievalAppService manualTextRetrievalAppService) {
        this.manualTextRetrievalAppService = manualTextRetrievalAppService;
    }

    @PostMapping("/index")
    public ApiResponse<ManualTextIndexResult> index(@Valid @RequestBody ManualTextIndexCommand command) {
        return ApiResponse.success(manualTextRetrievalAppService.indexManualText(command));
    }

    @PostMapping("/search")
    public ApiResponse<ManualTextSearchResult> search(@Valid @RequestBody ManualTextSearchCommand command) {
        return ApiResponse.success(manualTextRetrievalAppService.searchManualText(command));
    }
}
