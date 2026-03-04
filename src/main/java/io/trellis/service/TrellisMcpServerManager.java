package io.trellis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.dto.McpClientSession;
import io.trellis.entity.McpEndpointEntity;
import io.trellis.entity.McpSettingsEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.engine.WorkflowEngine;
import io.trellis.exception.NotFoundException;
import io.trellis.entity.ProjectEntity;
import io.trellis.repository.McpEndpointRepository;
import io.trellis.repository.McpSettingsRepository;
import io.trellis.repository.ProjectRepository;
import io.trellis.repository.WorkflowRepository;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrellisMcpServerManager {

    private final WorkflowRepository workflowRepository;
    private final ProjectRepository projectRepository;
    private final McpEndpointRepository endpointRepository;
    private final McpSettingsRepository settingsRepository;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final McpSystemToolService mcpSystemToolService;

    private static final List<String> SUPPORTED_VERSIONS = List.of(
            "2025-03-26", "2024-11-05");

    private final Map<String, McpToolDef> tools = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ClientSessionInfo> clientSessions = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile String currentToolsHash = "";

    @PostConstruct
    public void init() {
        boolean enabled = settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::isEnabled)
                .orElse(false);
        if (enabled) {
            startAll();
        }
    }

    public void startAll() {
        refreshTools();
        running = true;
        log.info("MCP server started with {} tools", tools.size());
    }

    public void stopAll() {
        running = false;
        emitters.values().forEach(SseEmitter::complete);
        emitters.clear();
        clientSessions.clear();
        tools.clear();
        log.info("MCP server stopped");
    }

    public void refreshTools() {
        tools.clear();
        // Pre-load project context paths to avoid N+1 queries
        Map<String, String> contextPaths = new HashMap<>();
        projectRepository.findAll().forEach(p -> {
            if (p.getContextPath() != null && !p.getContextPath().isBlank()) {
                contextPaths.put(p.getId(), p.getContextPath());
            }
        });

        List<WorkflowEntity> workflows = workflowRepository.findByMcpEnabledTrue();
        StringBuilder hashInput = new StringBuilder();
        for (WorkflowEntity wf : workflows) {
            String baseName = toSnakeCase(wf.getName());
            String contextPath = wf.getProjectId() != null ? contextPaths.get(wf.getProjectId()) : null;
            String toolName = contextPath != null
                    ? toSnakeCase(contextPath) + "__" + baseName
                    : baseName;

            if (tools.containsKey(toolName)) {
                log.warn("Duplicate MCP tool name '{}' — workflow '{}' (id={}) overwrites a previous entry",
                        toolName, wf.getName(), wf.getId());
            }

            String description = wf.getMcpDescription() != null ? wf.getMcpDescription()
                    : wf.getDescription() != null ? wf.getDescription()
                    : "Execute workflow: " + wf.getName();
            tools.put(toolName, new McpToolDef(toolName, description, wf.getId(), wf.getMcpInputSchema(), wf.getMcpOutputSchema()));

            // Build hash input: tool identity + description + schemas
            hashInput.append(toolName).append('|')
                    .append(description).append('|')
                    .append(wf.getId()).append('|')
                    .append(wf.getMcpInputSchema()).append('|')
                    .append(wf.getMcpOutputSchema()).append('\n');
        }

        String newHash = computeHash(hashInput.toString());
        boolean changed = !newHash.equals(currentToolsHash);
        currentToolsHash = newHash;

        // Persist hash so other cluster instances can detect the change
        settingsRepository.findFirstByOrderByCreatedAtAsc().ifPresent(settings -> {
            settings.setToolsHash(newHash);
            settingsRepository.save(settings);
        });

        if (changed && running) {
            notifyToolsChanged();
        }

        log.info("Refreshed MCP tools: {}", tools.keySet());
    }

    /**
     * Polls the database for tool configuration changes made by other cluster instances.
     * Compares the persisted tools hash against the local hash and refreshes if different.
     */
    @Scheduled(fixedDelay = 10_000)
    public void pollForToolChanges() {
        if (!running) return;

        settingsRepository.findFirstByOrderByCreatedAtAsc().ifPresent(settings -> {
            // Check if MCP was disabled by another instance
            if (!settings.isEnabled()) {
                log.info("MCP server disabled by another instance, stopping");
                stopAll();
                return;
            }

            String dbHash = settings.getToolsHash();
            if (dbHash != null && !dbHash.equals(currentToolsHash)) {
                log.info("MCP tools hash changed (db={}, local={}), refreshing", dbHash, currentToolsHash);
                refreshTools();
            }
        });
    }

    /**
     * Sends a tools/list_changed notification to all connected SSE clients
     * so they know to re-fetch the tools list.
     */
    private void notifyToolsChanged() {
        Map<String, Object> notification = new LinkedHashMap<>();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/tools/list_changed");

        List<String> deadSessions = new ArrayList<>();
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(notification),
                                org.springframework.http.MediaType.TEXT_PLAIN));
            } catch (IOException e) {
                deadSessions.add(entry.getKey());
            }
        }
        deadSessions.forEach(this::cleanup);
    }

    public boolean isRunning() {
        return running;
    }

    /**
     * Returns true when agent tools are enabled but NOT in dedicated mode,
     * meaning they should be combined with workflow tools on this server.
     */
    private boolean shouldIncludeSystemTools() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(s -> s.isAgentToolsEnabled() && !s.isAgentToolsDedicated())
                .orElse(false);
    }

    // --- SSE Transport ---

    public SseEmitter handleSseConnect(String endpointPath) {
        if (!running) {
            throw new IllegalStateException("MCP server is not running");
        }

        McpEndpointEntity endpoint = endpointRepository.findByPath(endpointPath)
                .orElseThrow(() -> new NotFoundException("MCP endpoint not found: " + endpointPath));

        if (!endpoint.isEnabled()) {
            throw new IllegalStateException("MCP endpoint is disabled");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        emitters.put(sessionId, emitter);
        clientSessions.put(sessionId, new ClientSessionInfo(
                sessionId, endpoint.getId(), endpoint.getName(), endpoint.getTransport(), Instant.now()));

        emitter.onCompletion(() -> cleanup(sessionId));
        emitter.onTimeout(() -> cleanup(sessionId));
        emitter.onError(e -> cleanup(sessionId));

        try {
            String messageUrl = "/mcp/" + endpointPath + "?sessionId=" + sessionId;
            emitter.send(SseEmitter.event().name("endpoint").data(messageUrl));
        } catch (IOException e) {
            log.error("Failed to send endpoint event", e);
            cleanup(sessionId);
        }

        return emitter;
    }

    public void handleSseMessage(String sessionId, Map<String, Object> message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            throw new NotFoundException("Session not found: " + sessionId);
        }

        ClientSessionInfo session = clientSessions.get(sessionId);
        if (session != null) {
            session.setLastSeenAt(Instant.now());
        }

        String method = (String) message.get("method");
        log.debug("MCP SSE recv [session={}]: method={}, id={}", sessionId, method, message.get("id"));

        Map<String, Object> response = processMessage(message, session);
        if (response != null) {
            try {
                log.debug("MCP SSE send [session={}]: {}", sessionId, response.get("id"));
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(response),
                                org.springframework.http.MediaType.TEXT_PLAIN));
            } catch (IOException e) {
                log.error("Failed to send SSE response", e);
            }
        }
    }

    // --- Streamable HTTP Transport ---

    public McpHandleResult handleStreamableHttpMessage(String endpointPath, String mcpSessionId,
                                                       Map<String, Object> message) {
        if (!running) {
            throw new IllegalStateException("MCP server is not running");
        }

        String method = (String) message.get("method");
        log.debug("MCP HTTP recv [path={}, session={}]: method={}, id={}", endpointPath, mcpSessionId, method, message.get("id"));
        ClientSessionInfo session = mcpSessionId != null ? clientSessions.get(mcpSessionId) : null;
        String returnSessionId = mcpSessionId;

        if ("initialize".equals(method) && session == null) {
            McpEndpointEntity endpoint = endpointRepository.findByPath(endpointPath)
                    .orElseThrow(() -> new NotFoundException("MCP endpoint not found: " + endpointPath));
            returnSessionId = UUID.randomUUID().toString();
            session = new ClientSessionInfo(
                    returnSessionId, endpoint.getId(), endpoint.getName(), endpoint.getTransport(), Instant.now());
            clientSessions.put(returnSessionId, session);
        }

        if (session != null) {
            session.setLastSeenAt(Instant.now());
        }

        Map<String, Object> response = processMessage(message, session);
        return new McpHandleResult(response, returnSessionId);
    }

    // --- Protocol Processing ---

    private Map<String, Object> processMessage(Map<String, Object> message, ClientSessionInfo session) {
        String method = (String) message.get("method");
        Object id = message.get("id");

        // Notifications have no id — no response needed
        if (id == null) {
            return null;
        }

        return switch (method) {
            case "initialize" -> handleInitialize(id, message, session);
            case "tools/list" -> handleToolsList(id);
            case "tools/call" -> handleToolsCall(id, message);
            case "ping" -> jsonRpcResult(id, Map.of());
            default -> jsonRpcError(id, -32601, "Method not found: " + method);
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleInitialize(Object id, Map<String, Object> message,
                                                  ClientSessionInfo session) {
        Map<String, Object> params = (Map<String, Object>) message.get("params");
        String clientVersion = null;
        if (params != null) {
            clientVersion = (String) params.get("protocolVersion");
            if (session != null) {
                Map<String, Object> clientInfo = (Map<String, Object>) params.get("clientInfo");
                if (clientInfo != null) {
                    session.setClientName((String) clientInfo.get("name"));
                    session.setClientVersion((String) clientInfo.get("version"));
                }
            }
        }

        // Negotiate protocol version — respond with the client's requested version
        // if we support it, otherwise fall back to our latest
        String negotiatedVersion = SUPPORTED_VERSIONS.contains(clientVersion)
                ? clientVersion : SUPPORTED_VERSIONS.get(0);
        log.info("MCP initialize: client requested={}, negotiated={}", clientVersion, negotiatedVersion);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedVersion);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", "Trellis", "version", "1.0.0"));
        return jsonRpcResult(id, result);
    }

    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> toolList = new ArrayList<>(tools.values().stream()
                .map(tool -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("name", tool.name);
                    t.put("description", tool.description);
                    t.put("inputSchema", buildInputSchema(tool.mcpInputSchema));
                    Map<String, Object> outputSchema = buildOutputSchema(tool.mcpOutputSchema);
                    if (outputSchema != null) {
                        t.put("outputSchema", outputSchema);
                    }
                    return t;
                })
                .toList());

        // In combined mode, append system tools alongside workflow tools
        if (shouldIncludeSystemTools()) {
            toolList.addAll(mcpSystemToolService.getSystemToolDefinitions());
        }

        return jsonRpcResult(id, Map.of("tools", toolList));
    }

    private Map<String, Object> buildInputSchema(Object mcpInputSchema) {
        if (mcpInputSchema instanceof List<?> paramList && !paramList.isEmpty()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();
            for (Object item : paramList) {
                if (item instanceof Map<?, ?> param) {
                    String name = (String) param.get("name");
                    String type = (String) param.get("type");
                    String description = (String) param.get("description");
                    Boolean isRequired = (Boolean) param.get("required");
                    if (name == null || name.isBlank()) continue;
                    Map<String, Object> prop = new LinkedHashMap<>();
                    prop.put("type", type != null ? type : "string");
                    if (description != null && !description.isBlank()) {
                        prop.put("description", description);
                    }
                    properties.put(name, prop);
                    if (Boolean.TRUE.equals(isRequired)) {
                        required.add(name);
                    }
                }
            }
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");
            schema.put("properties", properties);
            if (!required.isEmpty()) {
                schema.put("required", required);
            }
            return schema;
        }
        // Fallback: generic input string
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "input", Map.of(
                                "type", "string",
                                "description", "Input data for the workflow"
                        )
                )
        );
    }

    private Map<String, Object> buildOutputSchema(Object mcpOutputSchema) {
        if (!(mcpOutputSchema instanceof Map<?, ?> schemaMap)) return null;
        String format = (String) schemaMap.get("format");
        if (!"json".equals(format)) return null;

        List<?> properties = (List<?>) schemaMap.get("properties");
        if (properties == null || properties.isEmpty()) return null;

        Map<String, Object> schemaProps = new LinkedHashMap<>();
        for (Object item : properties) {
            if (item instanceof Map<?, ?> prop) {
                String name = (String) prop.get("name");
                String type = (String) prop.get("type");
                String description = (String) prop.get("description");
                if (name == null || name.isBlank()) continue;
                Map<String, Object> propSchema = new LinkedHashMap<>();
                propSchema.put("type", type != null ? type : "string");
                if (description != null && !description.isBlank()) {
                    propSchema.put("description", description);
                }
                schemaProps.put(name, propSchema);
            }
        }

        if (schemaProps.isEmpty()) return null;

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", schemaProps);
        String schemaDescription = (String) schemaMap.get("description");
        if (schemaDescription != null && !schemaDescription.isBlank()) {
            schema.put("description", schemaDescription);
        }
        return schema;
    }

    private Object extractStructuredContent(List<Map<String, Object>> result) {
        // Extract the json data from the first item of the workflow result
        if (result != null && !result.isEmpty()) {
            Map<String, Object> firstItem = result.get(0);
            if (firstItem != null && firstItem.containsKey("json")) {
                return firstItem.get("json");
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> message) {
        Map<String, Object> params = (Map<String, Object>) message.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        // In combined mode, delegate system tool calls
        if (shouldIncludeSystemTools() && mcpSystemToolService.isSystemTool(toolName)) {
            Map<String, Object> result = mcpSystemToolService.handleToolCall(toolName, arguments);
            return jsonRpcResult(id, result);
        }

        McpToolDef tool = tools.get(toolName);
        if (tool == null) {
            return jsonRpcError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            List<Map<String, Object>> inputItems;
            if (tool.mcpInputSchema instanceof List<?> paramList && !paramList.isEmpty()) {
                // Custom schema: pass all arguments directly as the JSON data item
                inputItems = List.of(Map.of("json", arguments != null ? arguments : Map.of()));
            } else {
                // Legacy: single input string
                String input = arguments != null
                        ? String.valueOf(arguments.getOrDefault("input", ""))
                        : "";
                inputItems = List.of(Map.of("json", Map.of("input", input)));
            }

            List<Map<String, Object>> result = workflowEngine.executeSubWorkflow(
                    tool.workflowId, inputItems);

            String resultText = objectMapper.writeValueAsString(result);

            // Format response based on output schema
            Map<String, Object> outputSchema = buildOutputSchema(tool.mcpOutputSchema);
            if (outputSchema != null) {
                // JSON format with structured output: include structuredContent + text fallback
                Map<String, Object> resultMap = new LinkedHashMap<>();
                resultMap.put("content", List.of(Map.of("type", "text", "text", resultText)));
                resultMap.put("structuredContent", extractStructuredContent(result));
                return jsonRpcResult(id, resultMap);
            }

            return jsonRpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", resultText))));
        } catch (Exception e) {
            log.error("Error executing MCP tool: {}", toolName, e);
            return jsonRpcResult(id, Map.of(
                    "content", List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())),
                    "isError", true));
        }
    }

    // --- JSON-RPC helpers ---

    private Map<String, Object> jsonRpcResult(Object id, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }

    // --- Session management ---

    public List<McpClientSession> getClientSessions() {
        return clientSessions.values().stream()
                .map(s -> McpClientSession.builder()
                        .sessionId(s.sessionId)
                        .endpointId(s.endpointId)
                        .endpointName(s.endpointName)
                        .transport(s.transport)
                        .clientName(s.clientName)
                        .clientVersion(s.clientVersion)
                        .connectedAt(s.connectedAt)
                        .lastSeenAt(s.lastSeenAt)
                        .build())
                .toList();
    }

    public void closeSession(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
        clientSessions.remove(sessionId);
    }

    private void cleanup(String sessionId) {
        emitters.remove(sessionId);
        clientSessions.remove(sessionId);
    }

    private String toSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("[\\s\\-]+", "_")
                .replaceAll("[^a-zA-Z0-9_]", "")
                .toLowerCase();
    }

    private String computeHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            // Fallback: use hashCode if SHA-256 unavailable (should never happen)
            return Integer.toHexString(input.hashCode());
        }
    }

    // --- Inner types ---

    public record McpHandleResult(Map<String, Object> body, String sessionId) {}

    private record McpToolDef(String name, String description, String workflowId, Object mcpInputSchema, Object mcpOutputSchema) {}

    @Data
    @AllArgsConstructor
    private static class ClientSessionInfo {
        private String sessionId;
        private String endpointId;
        private String endpointName;
        private String transport;
        private Instant connectedAt;
        private Instant lastSeenAt;
        private String clientName;
        private String clientVersion;

        ClientSessionInfo(String sessionId, String endpointId, String endpointName,
                          String transport, Instant connectedAt) {
            this.sessionId = sessionId;
            this.endpointId = endpointId;
            this.endpointName = endpointName;
            this.transport = transport;
            this.connectedAt = connectedAt;
            this.lastSeenAt = connectedAt;
        }
    }
}
