package io.cwc.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.cwc.service.AgentMcpServer;
import io.cwc.service.CwcMcpServerManager;
import io.cwc.service.CwcMcpServerManager.McpHandleResult;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
@ConditionalOnClass(name = "org.springframework.ai.mcp.server.autoconfigure.McpServerAutoConfiguration")
@ConditionalOnProperty(name = "cwc.features.mcp-server.enabled", havingValue = "true", matchIfMissing = true)
public class McpTransportController {

    private final CwcMcpServerManager mcpServerManager;
    private final AgentMcpServer agentMcpServer;

    @GetMapping(value = "/{path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sseConnect(@PathVariable String path) {
        return doSseConnect(path);
    }

    @GetMapping(value = "/{path}/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sseConnectDeep(HttpServletRequest request) {
        return doSseConnect(extractPath(request));
    }

    private ResponseEntity<SseEmitter> doSseConnect(String path) {
        // Route to agent MCP server if path matches
        if (agentMcpServer.matchesPath(path)) {
            if (!agentMcpServer.isEnabled()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            return ResponseEntity.ok(agentMcpServer.handleSseConnect(path));
        }
        if (!mcpServerManager.isRunning()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(mcpServerManager.handleSseConnect(path));
    }

    @PostMapping("/{path}")
    public ResponseEntity<Object> handleMessage(
            @PathVariable String path,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId,
            @RequestBody Map<String, Object> body) {
        return doHandleMessage(path, sessionId, mcpSessionId, body);
    }

    @PostMapping("/{path}/**")
    public ResponseEntity<Object> handleMessageDeep(
            HttpServletRequest request,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId,
            @RequestBody Map<String, Object> body) {
        return doHandleMessage(extractPath(request), sessionId, mcpSessionId, body);
    }

    private ResponseEntity<Object> doHandleMessage(String path, String sessionId,
                                                    String mcpSessionId, Map<String, Object> body) {
        if (sessionId != null) {
            // SSE transport — check if this session belongs to the agent server
            if (agentMcpServer.hasSession(sessionId)) {
                agentMcpServer.handleSseMessage(sessionId, body);
                return ResponseEntity.accepted().build();
            }
            if (!mcpServerManager.isRunning()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            mcpServerManager.handleSseMessage(sessionId, body);
            return ResponseEntity.accepted().build();
        }

        // Streamable HTTP transport — route by path
        if (agentMcpServer.matchesPath(path)) {
            if (!agentMcpServer.isEnabled()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
            }
            AgentMcpServer.McpHandleResult result =
                    agentMcpServer.handleStreamableHttpMessage(path, mcpSessionId, body);

            HttpHeaders headers = new HttpHeaders();
            if (result.sessionId() != null) {
                headers.set("Mcp-Session-Id", result.sessionId());
            }
            if (result.body() == null) {
                return ResponseEntity.accepted().headers(headers).build();
            }
            return ResponseEntity.ok().headers(headers).body(result.body());
        }

        if (!mcpServerManager.isRunning()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }

        McpHandleResult result = mcpServerManager.handleStreamableHttpMessage(path, mcpSessionId, body);

        HttpHeaders headers = new HttpHeaders();
        if (result.sessionId() != null) {
            headers.set("Mcp-Session-Id", result.sessionId());
        }

        // Notifications return null body — respond with 202 Accepted
        if (result.body() == null) {
            return ResponseEntity.accepted().headers(headers).build();
        }

        return ResponseEntity.ok().headers(headers).body(result.body());
    }

    @DeleteMapping("/{path}")
    public ResponseEntity<Void> closeSession(
            @PathVariable String path,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId) {
        return doCloseSession(mcpSessionId);
    }

    @DeleteMapping("/{path}/**")
    public ResponseEntity<Void> closeSessionDeep(
            HttpServletRequest request,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId) {
        return doCloseSession(mcpSessionId);
    }

    private ResponseEntity<Void> doCloseSession(String mcpSessionId) {
        if (mcpSessionId != null) {
            // Try agent server first, then fall back to workflow server
            if (agentMcpServer.hasSession(mcpSessionId)) {
                agentMcpServer.closeSession(mcpSessionId);
            } else {
                mcpServerManager.closeSession(mcpSessionId);
            }
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Extracts the path after /mcp/ from the request URI.
     * For SSE endpoints, strips the trailing /sse suffix to get the endpoint path.
     */
    /**
     * Token management page — served as self-contained HTML at {mcp-url}/token-management.
     * Allows users to create, view, and delete API tokens for this MCP endpoint.
     */
    @GetMapping(value = "/{path}/token-management", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tokenManagement(@PathVariable String path) {
        return ResponseEntity.ok(io.cwc.util.McpTokenPageGenerator.generate(path));
    }

    @GetMapping(value = "/{path}/{sub}/token-management", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> tokenManagementNested(@PathVariable String path, @PathVariable String sub) {
        return ResponseEntity.ok(io.cwc.util.McpTokenPageGenerator.generate(path + "/" + sub));
    }

    private String extractPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String path = uri.substring("/mcp/".length());
        // Strip trailing /sse for SSE connect requests
        if (path.endsWith("/sse")) {
            path = path.substring(0, path.length() - "/sse".length());
        }
        return path;
    }
}
