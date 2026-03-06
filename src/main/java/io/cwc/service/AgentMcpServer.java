package io.cwc.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.entity.McpSettingsEntity;
import io.cwc.repository.McpSettingsRepository;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dedicated MCP server for AI Agent system tools.
 * Completely separate from the workflow-based MCP server.
 * Serves only system tools (cwc_*) on its own configurable endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentMcpServer {

    private final McpSettingsRepository settingsRepository;
    private final McpSystemToolService mcpSystemToolService;
    private final ObjectMapper objectMapper;

    private static final List<String> SUPPORTED_VERSIONS = List.of(
            "2025-03-26", "2024-11-05");

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final Map<String, ClientSessionInfo> clientSessions = new ConcurrentHashMap<>();

    // --- Configuration ---

    public boolean isEnabled() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::isAgentToolsEnabled)
                .orElse(false);
    }

    public String getPath() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::getAgentToolsPath)
                .orElse("agent");
    }

    public String getTransport() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::getAgentToolsTransport)
                .orElse("STREAMABLE_HTTP");
    }

    public boolean isDedicated() {
        return settingsRepository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::isAgentToolsDedicated)
                .orElse(true);
    }

    /**
     * Check if a given path matches the agent tools endpoint path.
     * Only matches when running in dedicated mode.
     */
    public boolean matchesPath(String path) {
        return isEnabled() && isDedicated() && getPath().equals(path);
    }

    // --- SSE Transport ---

    public SseEmitter handleSseConnect(String endpointPath) {
        if (!isEnabled()) {
            throw new IllegalStateException("Agent tools MCP server is not enabled");
        }

        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(0L); // no timeout

        emitters.put(sessionId, emitter);
        clientSessions.put(sessionId, new ClientSessionInfo(
                sessionId, "agent-tools", getTransport(), Instant.now()));

        emitter.onCompletion(() -> cleanup(sessionId));
        emitter.onTimeout(() -> cleanup(sessionId));
        emitter.onError(e -> cleanup(sessionId));

        try {
            String messageUrl = "/mcp/" + endpointPath + "?sessionId=" + sessionId;
            emitter.send(SseEmitter.event().name("endpoint").data(messageUrl));
        } catch (IOException e) {
            log.error("Failed to send agent SSE endpoint event", e);
            cleanup(sessionId);
        }

        return emitter;
    }

    public void handleSseMessage(String sessionId, Map<String, Object> message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            throw new IllegalStateException("Agent session not found: " + sessionId);
        }

        ClientSessionInfo session = clientSessions.get(sessionId);
        if (session != null) {
            session.setLastSeenAt(Instant.now());
        }

        Map<String, Object> response = processMessage(message, session);
        if (response != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(objectMapper.writeValueAsString(response),
                                org.springframework.http.MediaType.TEXT_PLAIN));
            } catch (IOException e) {
                log.error("Failed to send agent SSE response", e);
            }
        }
    }

    // --- Streamable HTTP Transport ---

    public McpHandleResult handleStreamableHttpMessage(String endpointPath, String mcpSessionId,
                                                        Map<String, Object> message) {
        if (!isEnabled()) {
            throw new IllegalStateException("Agent tools MCP server is not enabled");
        }

        String method = (String) message.get("method");
        log.debug("Agent MCP recv [path={}, session={}]: method={}", endpointPath, mcpSessionId, method);
        ClientSessionInfo session = mcpSessionId != null ? clientSessions.get(mcpSessionId) : null;
        String returnSessionId = mcpSessionId;

        if ("initialize".equals(method) && session == null) {
            returnSessionId = UUID.randomUUID().toString();
            session = new ClientSessionInfo(
                    returnSessionId, "agent-tools", getTransport(), Instant.now());
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

        if (id == null) {
            return null; // notification — no response
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

        String negotiatedVersion = SUPPORTED_VERSIONS.contains(clientVersion)
                ? clientVersion : SUPPORTED_VERSIONS.get(0);
        log.info("Agent MCP initialize: client requested={}, negotiated={}", clientVersion, negotiatedVersion);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", negotiatedVersion);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", "CWC Agent Tools", "version", "1.0.0"));
        return jsonRpcResult(id, result);
    }

    private Map<String, Object> handleToolsList(Object id) {
        List<Map<String, Object>> toolList = mcpSystemToolService.getSystemToolDefinitions();
        return jsonRpcResult(id, Map.of("tools", toolList));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> handleToolsCall(Object id, Map<String, Object> message) {
        Map<String, Object> params = (Map<String, Object>) message.get("params");
        String toolName = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if (!mcpSystemToolService.isSystemTool(toolName)) {
            return jsonRpcError(id, -32602, "Unknown tool: " + toolName);
        }

        try {
            Map<String, Object> result = mcpSystemToolService.handleToolCall(toolName, arguments);
            return jsonRpcResult(id, result);
        } catch (Exception e) {
            log.error("Error executing agent tool: {}", toolName, e);
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

    public void closeSession(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
        clientSessions.remove(sessionId);
    }

    /**
     * Check if this sessionId belongs to the agent MCP server (for SSE routing).
     */
    public boolean hasSession(String sessionId) {
        return emitters.containsKey(sessionId) || clientSessions.containsKey(sessionId);
    }

    private void cleanup(String sessionId) {
        emitters.remove(sessionId);
        clientSessions.remove(sessionId);
    }

    // --- Inner types ---

    public record McpHandleResult(Map<String, Object> body, String sessionId) {}

    @Data
    @AllArgsConstructor
    private static class ClientSessionInfo {
        private String sessionId;
        private String endpointName;
        private String transport;
        private Instant connectedAt;
        private Instant lastSeenAt;
        private String clientName;
        private String clientVersion;

        ClientSessionInfo(String sessionId, String endpointName,
                          String transport, Instant connectedAt) {
            this.sessionId = sessionId;
            this.endpointName = endpointName;
            this.transport = transport;
            this.connectedAt = connectedAt;
            this.lastSeenAt = connectedAt;
        }
    }
}
