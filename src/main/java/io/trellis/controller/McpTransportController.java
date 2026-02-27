package io.trellis.controller;

import io.trellis.service.TrellisMcpServerManager;
import io.trellis.service.TrellisMcpServerManager.McpHandleResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

    @GetMapping(value = "/{path}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseConnect(@PathVariable String path) {
        return mcpServerManager.handleSseConnect(path);
    }

    @PostMapping("/{path}")
    public ResponseEntity<Object> handleMessage(
            @PathVariable String path,
            @RequestParam(required = false) String sessionId,
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId,
            @RequestBody Map<String, Object> body) {

        if (sessionId != null) {
            // SSE transport — response goes via the SseEmitter
            mcpServerManager.handleSseMessage(sessionId, body);
            return ResponseEntity.accepted().build();
        }

        // Streamable HTTP transport — response in body
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
            @RequestHeader(value = "Mcp-Session-Id", required = false) String mcpSessionId) {
        if (mcpSessionId != null) {
            mcpServerManager.closeSession(mcpSessionId);
        }
        return ResponseEntity.noContent().build();
    }
}
