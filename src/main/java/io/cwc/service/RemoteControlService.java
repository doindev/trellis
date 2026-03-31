package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.dto.ToolConsentResult;
import io.cwc.service.ToolApiMapping.ApiCallSpec;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteControlService {

    private final WebSocketService webSocketService;

    private final ConcurrentHashMap<String, CompletableFuture<ToolConsentResult>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Request user consent for a tool call. Sends a WebSocket message with the API spec
     * and blocks until the browser responds with the result or the timeout expires.
     */
    public ToolConsentResult requestToolConsent(String browserSessionId, String toolName,
                                                 String description, Map<String, Object> arguments,
                                                 ApiCallSpec apiSpec) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<ToolConsentResult> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            webSocketService.sendToolConsentRequest(browserSessionId, requestId, toolName,
                    description, arguments, apiSpec);

            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Tool consent request timed out or failed: {} (tool: {})", requestId, toolName);
            return ToolConsentResult.timeout();
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * Resolve a pending request with the browser's result.
     */
    public void resolveRequest(String requestId, ToolConsentResult result) {
        CompletableFuture<ToolConsentResult> future = pendingRequests.get(requestId);
        if (future != null) {
            future.complete(result);
            log.info("Tool consent request {} {}", requestId,
                    result.isApproved() ? "approved" : "denied");
        }
    }

    /**
     * Deny all pending requests (e.g. when user revokes control).
     */
    public void revokeAll() {
        pendingRequests.values().forEach(f -> f.complete(ToolConsentResult.revoked()));
        pendingRequests.clear();
        log.info("All pending tool consent requests revoked");
    }
}
