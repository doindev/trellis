package io.cwc.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import io.cwc.dto.McpClientSession;
import io.cwc.dto.McpEndpointDto;
import io.cwc.dto.McpServerInfo;
import io.cwc.dto.McpSettingsDto;
import io.cwc.service.McpSettingsService;

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

    @PutMapping("/agent-tools")
    public McpSettingsDto updateAgentToolsSettings(@RequestBody Map<String, Object> body) {
        Boolean enabled = body.containsKey("agentToolsEnabled")
                ? Boolean.TRUE.equals(body.get("agentToolsEnabled")) : null;
        Boolean dedicated = body.containsKey("agentToolsDedicated")
                ? Boolean.TRUE.equals(body.get("agentToolsDedicated")) : null;
        String path = (String) body.get("agentToolsPath");
        String transport = (String) body.get("agentToolsTransport");
        return mcpSettingsService.updateAgentToolsSettings(enabled, dedicated, path, transport);
    }

    // --- Servers ---

    @GetMapping("/servers")
    public List<McpServerInfo> getMcpServers() {
        return mcpSettingsService.getMcpServers();
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
        if (body.containsKey("mcpInputSchema")) {
            mcpSettingsService.updateWorkflowMcpInputSchema(workflowId, body.get("mcpInputSchema"));
        }
        if (body.containsKey("mcpOutputSchema")) {
            mcpSettingsService.updateWorkflowMcpOutputSchema(workflowId, body.get("mcpOutputSchema"));
        }
    }

    @GetMapping("/workflows/{workflowId}/auto-detect-params")
    public List<Map<String, Object>> autoDetectParams(@PathVariable String workflowId) {
        return mcpSettingsService.autoDetectParameters(workflowId);
    }

    @DeleteMapping("/workflows/{workflowId}")
    public void revokeWorkflow(@PathVariable String workflowId) {
        mcpSettingsService.setWorkflowMcpEnabled(workflowId, false);
    }
}
