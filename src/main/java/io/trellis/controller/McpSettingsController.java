package io.trellis.controller;

import io.trellis.dto.McpSettingsDto;
import io.trellis.service.McpSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings/mcp")
@RequiredArgsConstructor
public class McpSettingsController {

    private final McpSettingsService mcpSettingsService;

    @GetMapping
    public McpSettingsDto getSettings() {
        return mcpSettingsService.getSettings();
    }

    @PutMapping
    public McpSettingsDto updateSettings(@RequestBody McpSettingsDto dto) {
        return mcpSettingsService.setEnabled(dto.isEnabled());
    }

    @GetMapping("/workflows")
    public List<Map<String, Object>> getWorkflows() {
        return mcpSettingsService.getEnabledWorkflows().stream()
                .map(wf -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", wf.getId());
                    map.put("name", wf.getName());
                    map.put("mcpEnabled", wf.isMcpEnabled());
                    return map;
                })
                .toList();
    }

    @PutMapping("/workflows/{workflowId}")
    public void enableWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Boolean> body) {
        boolean enabled = body.getOrDefault("mcpEnabled", true);
        mcpSettingsService.setWorkflowMcpEnabled(workflowId, enabled);
    }

    @DeleteMapping("/workflows/{workflowId}")
    public void revokeWorkflow(@PathVariable String workflowId) {
        mcpSettingsService.setWorkflowMcpEnabled(workflowId, false);
    }
}
