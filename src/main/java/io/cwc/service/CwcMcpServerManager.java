package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.dto.McpClientSession;
import io.cwc.engine.WorkflowEngine;
import io.cwc.entity.McpClientSessionEntity;
import io.cwc.entity.McpEndpointEntity;
import io.cwc.entity.McpSettingsEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.entity.WorkflowVersionEntity;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.McpClientSessionRepository;
import io.cwc.repository.McpEndpointRepository;
import io.cwc.repository.McpSettingsRepository;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.WorkflowRepository;
import io.cwc.repository.WorkflowVersionRepository;
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
public class CwcMcpServerManager {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final ProjectRepository projectRepository;
    private final McpEndpointRepository endpointRepository;
    private final McpSettingsRepository settingsRepository;
    private final McpClientSessionRepository clientSessionRepository;
    private final WorkflowEngine workflowEngine;
    private final ObjectMapper objectMapper;
    private final McpSystemToolService mcpSystemToolService;

    private final String instanceId = UUID.randomUUID().toString();

    private static final List<String> SUPPORTED_VERSIONS = List.of(
            "2025-03-26", "2024-11-05");

    private final Map<String, McpToolDef> tools = new ConcurrentHashMap<>();
    private final Map<String, Map<String, McpToolDef>> projectTools = new ConcurrentHashMap<>();
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ClientSessionInfo> clientSessions = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile String currentToolsHash = "";

    @PostConstruct
    public void init() {
        // Mark any sessions from a previous lifecycle of this instance as disconnected
        clientSessionRepository.disconnectStaleByInstanceId(instanceId, Instant.now());

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
        clientSessions.forEach((id, session) -> persistDisconnect(id));
        clientSessions.clear();
        tools.clear();
        projectTools.clear();
        log.info("MCP server stopped");
    }

    public void refreshTools() {
        tools.clear();
        projectTools.clear();

        // Pre-load project context paths to avoid N+1 queries
        Map<String, String> contextPaths = new HashMap<>();
        projectRepository.findAll().forEach(p -> {
            if (p.getContextPath() != null && !p.getContextPath().isBlank()) {
                contextPaths.put(p.getId(), p.getContextPath());
            }
        });

        // Determine which projects have their own MCP endpoint
        Set<String> projectsWithEndpoint = new HashSet<>();
        endpointRepository.findAll().forEach(ep -> {
            if (ep.getProjectId() != null && ep.isEnabled()) {
                projectsWithEndpoint.add(ep.getProjectId());
            }
        });

        List<WorkflowEntity> workflows = workflowRepository.findByMcpEnabledTrue();

        // Batch-load latest published versions to resolve schemas from published state
        List<String> workflowIds = workflows.stream().map(WorkflowEntity::getId).toList();
        Map<String, WorkflowVersionEntity> publishedVersions = new HashMap<>();
        if (!workflowIds.isEmpty()) {
            workflowVersionRepository.findByWorkflowIdInAndPublishedTrueOrderByVersionNumberDesc(workflowIds)
                    .forEach(v -> publishedVersions.putIfAbsent(v.getWorkflowId(), v));
        }

        StringBuilder hashInput = new StringBuilder();
        for (WorkflowEntity wf : workflows) {
            String baseName = toSnakeCase(wf.getName());
            String contextPath = wf.getProjectId() != null ? contextPaths.get(wf.getProjectId()) : null;
            String toolName = contextPath != null
                    ? toSnakeCase(contextPath) + "__" + baseName
                    : baseName;

            String description = wf.getMcpDescription() != null ? wf.getMcpDescription()
                    : wf.getDescription() != null ? wf.getDescription()
                    : "Execute workflow: " + wf.getName();

            // Use schemas from the latest published version; fall back to current draft if never published
            WorkflowVersionEntity published = publishedVersions.get(wf.getId());
            Object inputSchema = published != null && published.getMcpInputSchema() != null
                    ? published.getMcpInputSchema() : wf.getMcpInputSchema();
            Object outputSchema = published != null && published.getMcpOutputSchema() != null
                    ? published.getMcpOutputSchema() : wf.getMcpOutputSchema();

            // Extract webhook path from workflow nodes for path param routing
            String webhookPath = extractWebhookPath(wf.getNodes());

            McpToolDef toolDef = new McpToolDef(toolName, description, wf.getId(),
                    inputSchema, outputSchema, webhookPath);

            // Route to project-scoped map or global map
            if (wf.getProjectId() != null && projectsWithEndpoint.contains(wf.getProjectId())) {
                Map<String, McpToolDef> pTools = projectTools.computeIfAbsent(
                        wf.getProjectId(), k -> new ConcurrentHashMap<>());
                if (pTools.containsKey(toolName)) {
                    log.warn("Duplicate MCP tool name '{}' in project {} — workflow '{}' (id={}) overwrites a previous entry",
                            toolName, wf.getProjectId(), wf.getName(), wf.getId());
                }
                pTools.put(toolName, toolDef);
            } else {
                if (tools.containsKey(toolName)) {
                    log.warn("Duplicate MCP tool name '{}' — workflow '{}' (id={}) overwrites a previous entry",
                            toolName, wf.getName(), wf.getId());
                }
                tools.put(toolName, toolDef);
            }

            // Build hash input: tool identity + description + schemas + webhookPath
            hashInput.append(toolName).append('|')
                    .append(description).append('|')
                    .append(wf.getId()).append('|')
                    .append(inputSchema).append('|')
                    .append(outputSchema).append('|')
                    .append(webhookPath).append('\n');
        }

        String newHash = computeHash(hashInput.toString());
        boolean changed = !newHash.equals(currentToolsHash);
        currentToolsHash = newHash;

        if (changed) {
            // Persist hash so other cluster instances can detect the change
            settingsRepository.findFirstByOrderByCreatedAtAsc().ifPresent(settings -> {
                if (!newHash.equals(settings.getToolsHash())) {
                    settings.setToolsHash(newHash);
                    settingsRepository.save(settings);
                }
            });

            if (running) {
                notifyToolsChanged();
            }

            log.info("Refreshed MCP tools: {} global, {} project-scoped", tools.size(), projectTools.size());
        }
    }

    /**
     * Polls for MCP state changes across cluster instances. Detects:
     * - MCP enabled by another instance → start locally
     * - MCP disabled by another instance → stop locally
     * - Tool configuration changes (workflow publish/unpublish, description/schema edits)
     */
    @Scheduled(fixedDelay = 10_000)
    public void pollForToolChanges() {
        settingsRepository.findFirstByOrderByCreatedAtAsc().ifPresent(settings -> {
            if (settings.isEnabled() && !running) {
                log.info("MCP server enabled by another instance, starting");
                startAll();
                return;
            }
            if (!settings.isEnabled() && running) {
                log.info("MCP server disabled by another instance, stopping");
                stopAll();
                return;
            }
        });

        if (!running) return;

        // Always refresh — idempotent when nothing changed (no DB write, no SSE notification)
        refreshTools();
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

        Instant now = Instant.now();
        emitters.put(sessionId, emitter);
        ClientSessionInfo sessionInfo = new ClientSessionInfo(
                sessionId, endpoint.getId(), endpoint.getName(), endpoint.getTransport(), now);
        sessionInfo.setProjectId(endpoint.getProjectId());
        clientSessions.put(sessionId, sessionInfo);
        persistNewSession(sessionInfo);

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
            persistLastSeenIfNeeded(session);
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
            Instant now = Instant.now();
            session = new ClientSessionInfo(
                    returnSessionId, endpoint.getId(), endpoint.getName(), endpoint.getTransport(), now);
            session.setProjectId(endpoint.getProjectId());
            clientSessions.put(returnSessionId, session);
            persistNewSession(session);
        }

        if (session != null) {
            session.setLastSeenAt(Instant.now());
            persistLastSeenIfNeeded(session);
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
            case "tools/list" -> handleToolsList(id, session);
            case "tools/call" -> handleToolsCall(id, message, session);
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
                    persistClientInfo(session);
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
        result.put("serverInfo", Map.of("name", "CWC", "version", "1.0.0"));
        return jsonRpcResult(id, result);
    }

    private Map<String, Object> handleToolsList(Object id, ClientSessionInfo session) {
        // Determine which tool map to use based on session scope
        Map<String, McpToolDef> effectiveTools;
        if (session != null && session.getProjectId() != null) {
            effectiveTools = projectTools.getOrDefault(session.getProjectId(), Map.of());
        } else {
            effectiveTools = tools;
        }

        List<Map<String, Object>> toolList = new ArrayList<>(effectiveTools.values().stream()
                .map(tool -> {
                    Map<String, Object> t = new LinkedHashMap<>();
                    t.put("name", tool.name);
                    t.put("description", tool.description);
                    // Auto-populate missing path params into inputSchema
                    List<String> pathParams = tool.webhookPath != null
                            ? io.cwc.util.SchemaUtils.extractPathParamNames(tool.webhookPath)
                            : List.of();
                    t.put("inputSchema", io.cwc.util.SchemaUtils.buildInputSchemaWithPathParams(
                            tool.mcpInputSchema, pathParams));
                    Map<String, Object> outputSchema = buildOutputSchema(tool.mcpOutputSchema);
                    if (outputSchema != null) {
                        t.put("outputSchema", outputSchema);
                    }
                    return t;
                })
                .toList());

        // In combined mode, append system tools alongside workflow tools (only for app-level sessions)
        if ((session == null || session.getProjectId() == null) && shouldIncludeSystemTools()) {
            toolList.addAll(mcpSystemToolService.getSystemToolDefinitions());
        }

        return jsonRpcResult(id, Map.of("tools", toolList));
    }

    @SuppressWarnings("unused")
	private Map<String, Object> buildInputSchema(Object mcpInputSchema) {
        return io.cwc.util.SchemaUtils.buildInputSchema(mcpInputSchema);
    }

    private Map<String, Object> buildOutputSchema(Object mcpOutputSchema) {
        return io.cwc.util.SchemaUtils.buildOutputSchema(mcpOutputSchema);
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
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> message, ClientSessionInfo session) {
        Map<String, Object> params = (Map<String, Object>) message.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        // In combined mode, delegate system tool calls (only for app-level sessions)
        if ((session == null || session.getProjectId() == null)
                && shouldIncludeSystemTools() && mcpSystemToolService.isSystemTool(toolName)) {
            Map<String, Object> result = mcpSystemToolService.handleToolCall(toolName, arguments);
            return jsonRpcResult(id, result);
        }

        // Look up tool from the appropriate scope
        McpToolDef tool;
        if (session != null && session.getProjectId() != null) {
            Map<String, McpToolDef> pTools = projectTools.getOrDefault(session.getProjectId(), Map.of());
            tool = pTools.get(toolName);
        } else {
            tool = tools.get(toolName);
        }
        if (tool == null) {
            return jsonRpcError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            // Route arguments based on the payload convention:
            // - "payload" argument → body
            // - argument matching webhook URL path param → pathParams
            // - everything else → queryParams
            // If no payload property in schema and no path params, fall back to legacy (all → body)
            List<String> pathParamNames = tool.webhookPath != null
                    ? io.cwc.util.SchemaUtils.extractPathParamNames(tool.webhookPath)
                    : List.of();
            boolean schemaHasPayload = io.cwc.util.SchemaUtils.hasPayloadProperty(tool.mcpInputSchema);

            Map<String, Object> webhookLike = new LinkedHashMap<>();

            if (!schemaHasPayload && pathParamNames.isEmpty()) {
                // Legacy mode: no payload convention, preserve existing behavior
                if (tool.mcpInputSchema instanceof List<?> paramList && !paramList.isEmpty()) {
                    webhookLike.put("body", arguments != null ? arguments : Map.of());
                } else if (tool.mcpInputSchema instanceof Map<?, ?>) {
                    // Direct JSON Schema without payload — all args to body
                    webhookLike.put("body", arguments != null ? arguments : Map.of());
                } else {
                    // Fallback: single input string
                    String input = arguments != null
                            ? String.valueOf(arguments.getOrDefault("input", ""))
                            : "";
                    webhookLike.put("body", Map.of("input", input));
                }
                webhookLike.put("headers", Map.of());
                webhookLike.put("queryParams", Map.of());
                webhookLike.put("pathParams", Map.of());
            } else {
                // New convention: route by payload/path/query
                Map<String, Object> bodyContent = new LinkedHashMap<>();
                Map<String, Object> queryArgs = new LinkedHashMap<>();
                Map<String, Object> pathArgs = new LinkedHashMap<>();

                if (arguments != null) {
                    for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                        if ("payload".equals(entry.getKey())) {
                            Object val = entry.getValue();
                            if (val instanceof Map<?, ?> mapVal) {
                                Map<String, Object> typedMap = (Map<String, Object>) mapVal;
                                bodyContent.putAll(typedMap);
                            } else {
                                bodyContent.put("payload", val);
                            }
                        } else if (pathParamNames.contains(entry.getKey())) {
                            pathArgs.put(entry.getKey(), entry.getValue());
                        } else {
                            queryArgs.put(entry.getKey(), entry.getValue());
                        }
                    }
                }

                webhookLike.put("body", bodyContent);
                webhookLike.put("headers", Map.of());
                webhookLike.put("queryParams", queryArgs);
                webhookLike.put("pathParams", pathArgs);
            }
            webhookLike.put("method", "MCP");
            List<Map<String, Object>> inputItems = List.of(Map.of("json", webhookLike));

            List<Map<String, Object>> result = workflowEngine.executeSubWorkflow(
                    tool.workflowId, inputItems, io.cwc.entity.ExecutionEntity.ExecutionMode.INTERNAL);

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
        Instant cutoff = Instant.now().minusSeconds(86400);

        // Build endpoint -> projectId lookup
        Map<String, String> endpointProjectMap = new HashMap<>();
        for (McpEndpointEntity ep : endpointRepository.findAll()) {
            endpointProjectMap.put(ep.getId(), ep.getProjectId());
        }

        return clientSessionRepository.findByLastSeenAtAfterOrderByDisconnectedAtAscConnectedAtDesc(cutoff)
                .stream()
                .map(e -> McpClientSession.builder()
                        .sessionId(e.getSessionId())
                        .endpointId(e.getEndpointId())
                        .endpointName(e.getEndpointName())
                        .transport(e.getTransport())
                        .clientName(e.getClientName())
                        .clientVersion(e.getClientVersion())
                        .projectId(endpointProjectMap.get(e.getEndpointId()))
                        .connectedAt(e.getConnectedAt())
                        .lastSeenAt(e.getLastSeenAt())
                        .disconnectedAt(e.getDisconnectedAt())
                        .build())
                .toList();
    }

    public void closeSession(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
        clientSessions.remove(sessionId);
        persistDisconnect(sessionId);
    }

    private void cleanup(String sessionId) {
        emitters.remove(sessionId);
        clientSessions.remove(sessionId);
        persistDisconnect(sessionId);
    }

    @Scheduled(fixedDelay = 3600_000)
    public void purgeOldSessions() {
        clientSessionRepository.deleteByLastSeenAtBefore(Instant.now().minusSeconds(86400));
    }

    private void persistNewSession(ClientSessionInfo session) {
        try {
            clientSessionRepository.save(McpClientSessionEntity.builder()
                    .sessionId(session.getSessionId())
                    .instanceId(instanceId)
                    .endpointId(session.getEndpointId())
                    .endpointName(session.getEndpointName())
                    .transport(session.getTransport())
                    .connectedAt(session.getConnectedAt())
                    .lastSeenAt(session.getConnectedAt())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist MCP client session: {}", e.getMessage());
        }
    }

    private void persistClientInfo(ClientSessionInfo session) {
        try {
            clientSessionRepository.findById(session.getSessionId()).ifPresent(entity -> {
                entity.setClientName(session.getClientName());
                entity.setClientVersion(session.getClientVersion());
                clientSessionRepository.save(entity);
            });
        } catch (Exception e) {
            log.warn("Failed to update MCP client info: {}", e.getMessage());
        }
    }

    private void persistLastSeenIfNeeded(ClientSessionInfo session) {
        Instant now = Instant.now();
        if (session.getLastDbWrite() == null || now.minusSeconds(30).isAfter(session.getLastDbWrite())) {
            try {
                clientSessionRepository.findById(session.getSessionId()).ifPresent(entity -> {
                    entity.setLastSeenAt(now);
                    clientSessionRepository.save(entity);
                });
                session.setLastDbWrite(now);
            } catch (Exception e) {
                log.warn("Failed to update MCP session lastSeenAt: {}", e.getMessage());
            }
        }
    }

    private void persistDisconnect(String sessionId) {
        try {
            clientSessionRepository.findById(sessionId).ifPresent(entity -> {
                if (entity.getDisconnectedAt() == null) {
                    entity.setDisconnectedAt(Instant.now());
                    clientSessionRepository.save(entity);
                }
            });
        } catch (Exception e) {
            log.warn("Failed to persist MCP session disconnect: {}", e.getMessage());
        }
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

    private String extractWebhookPath(Object nodesObj) {
        if (!(nodesObj instanceof List<?> nodes)) return null;
        for (Object nodeObj : nodes) {
            if (!(nodeObj instanceof Map<?, ?> node)) continue;
            if (!"webhook".equals(node.get("type"))) continue;
            Object params = node.get("parameters");
            if (params instanceof Map<?, ?> p) {
                String path = (String) p.get("path");
                if (path != null && !path.isEmpty()) return path;
            }
        }
        return null;
    }

    // --- Inner types ---

    public record McpHandleResult(Map<String, Object> body, String sessionId) {}

    private record McpToolDef(String name, String description, String workflowId,
                               Object mcpInputSchema, Object mcpOutputSchema, String webhookPath) {}

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
        private Instant lastDbWrite;
        private String projectId; // null = app-level, non-null = project-scoped

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
