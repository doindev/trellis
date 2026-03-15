package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.ExecutionSettingsDto;
import io.cwc.service.ExecutionSettingsService;
import io.cwc.service.ExecutionSettingsResolver;

import java.util.Map;

@RestController
@RequestMapping("/api/settings/execution")
@RequiredArgsConstructor
public class ExecutionSettingsController {

    private final ExecutionSettingsService executionSettingsService;
    private final ExecutionSettingsResolver executionSettingsResolver;

    @GetMapping
    public ExecutionSettingsDto getSettings() {
        return executionSettingsService.getSettings();
    }

    @PutMapping
    public ExecutionSettingsDto updateSettings(@RequestBody ExecutionSettingsDto dto) {
        return executionSettingsService.updateSettings(dto);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/resolve")
    public Map<String, Object> resolveSettings(@RequestBody Map<String, Object> body) {
        String projectId = (String) body.get("projectId");
        String workflowId = (String) body.get("workflowId");
        Map<String, Object> workflowSettings = body.get("workflowSettings") instanceof Map
                ? (Map<String, Object>) body.get("workflowSettings") : null;
        return executionSettingsResolver.resolveWithSources(workflowId, projectId, workflowSettings);
    }
}
