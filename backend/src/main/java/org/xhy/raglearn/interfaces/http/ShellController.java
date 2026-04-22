package org.xhy.raglearn.interfaces.http;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xhy.raglearn.application.ShellAppService;
import org.xhy.raglearn.common.api.ApiResponse;

/**
 * Minimal HTTP entry for the shell stage.
 */
@RestController
@RequestMapping("/v1/shell")
public class ShellController {

    private final ShellAppService shellAppService;

    public ShellController(ShellAppService shellAppService) {
        this.shellAppService = shellAppService;
    }

    @GetMapping("/ping")
    public ApiResponse<Map<String, Object>> ping() {
        return ApiResponse.success(Map.of(
                "status", "UP",
                "stage", "learn/00-shell",
                "timestamp", LocalDateTime.now().toString()
        ));
    }

    @GetMapping("/overview")
    public ApiResponse<?> overview() {
        return ApiResponse.success(shellAppService.buildOverview());
    }
}

