package io.trellis.controller;

import io.trellis.dto.McpClientSession;
import io.trellis.dto.McpEndpointDto;
import io.trellis.dto.McpSettingsDto;
import io.trellis.service.McpSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    // --- Endpoints ---

    @GetMapping("/endpoints")
    public List<McpEndpointDto> listEndpoints() {
        return mcpSettingsService.listEndpoints();
    }

    @PostMapping("/endpoints")
    public McpEndpointDto createEndpoint(@RequestBody McpEndpointDto dto) {
        return mcpSettingsService.createEndpoint(dto);
    }

    @PutMapping("/endpoints/{id}")
    public McpEndpointDto updateEndpoint(@PathVariable String id, @RequestBody McpEndpointDto dto) {
        return mcpSettingsService.updateEndpoint(id, dto);
    }

    @DeleteMapping("/endpoints/{id}")
    public void deleteEndpoint(@PathVariable String id) {
        mcpSettingsService.deleteEndpoint(id);
    }

    // --- Clients ---

    @GetMapping("/clients")
    public List<McpClientSession> listClients() {
        return mcpSettingsService.getClientSessions();
    }

    // --- Workflows ---

    @GetMapping("/workflows")
    public List<Map<String, Object>> getWorkflows() {
        return mcpSettingsService.getAllWorkflowsWithMcpStatus();
    }

    @PutMapping("/workflows/{workflowId}")
    public void updateWorkflow(@PathVariable String workflowId, @RequestBody Map<String, Object> body) {
        if (body.containsKey("mcpEnabled")) {
            boolean enabled = (Boolean) body.get("mcpEnabled");
            mcpSettingsService.setWorkflowMcpEnabled(workflowId, enabled);
        }
        if (body.containsKey("mcpDescription")) {
            String mcpDescription = (String) body.get("mcpDescription");
            mcpSettingsService.updateWorkflowMcpDescription(workflowId, mcpDescription);
        }
    }

    @DeleteMapping("/workflows/{workflowId}")
    public void revokeWorkflow(@PathVariable String workflowId) {
        mcpSettingsService.setWorkflowMcpEnabled(workflowId, false);
    }
}
