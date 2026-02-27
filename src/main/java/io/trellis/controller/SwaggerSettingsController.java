package io.trellis.controller;

import io.trellis.dto.SwaggerSettingsDto;
import io.trellis.service.SwaggerSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/swagger")
@RequiredArgsConstructor
public class SwaggerSettingsController {

    private final SwaggerSettingsService swaggerSettingsService;

    @GetMapping
    public SwaggerSettingsDto getSettings() {
        return swaggerSettingsService.getSettings();
    }

    @PutMapping
    public SwaggerSettingsDto updateSettings(@RequestBody SwaggerSettingsDto dto) {
        return swaggerSettingsService.updateSettings(dto);
    }

    @GetMapping("/workflows")
    public List<Map<String, Object>> getWorkflows() {
        return swaggerSettingsService.getAllWorkflowsWithSwaggerStatus();
    }

    @PutMapping("/workflows/{workflowId}")
    public void updateWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Object> body) {
        if (body.containsKey("swaggerEnabled")) {
            boolean enabled = (Boolean) body.get("swaggerEnabled");
            swaggerSettingsService.setWorkflowSwaggerEnabled(workflowId, enabled);
        }
    }
}
