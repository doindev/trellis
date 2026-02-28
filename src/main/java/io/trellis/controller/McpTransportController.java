package io.trellis.controller;

import io.trellis.service.AgentMcpServer;
import io.trellis.service.TrellisMcpServerManager;
import io.trellis.service.TrellisMcpServerManager.McpHandleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpTransportController {

    private final TrellisMcpServerManager mcpServerManager;
    private final AgentMcpServer agentMcpServer;

    @GetMapping(value = "/{path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> sseConnect(@PathVariable String path) {
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
}
