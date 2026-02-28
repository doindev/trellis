package io.trellis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> pendingRequests = new ConcurrentHashMap<>();

    /**
     * Request user consent for a tool call. Sends a WebSocket message
     * and blocks until the user responds or the timeout expires.
     *
     * @return true if user approved, false if denied or timed out
     */
    public boolean requestToolConsent(String browserSessionId, String toolName, String description, Map<String, Object> arguments) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        try {
            webSocketService.sendToolConsentRequest(browserSessionId, requestId, toolName, description, arguments);

            return future.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Tool consent request timed out or failed: {} (tool: {})", requestId, toolName, e);
            return false;
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * Resolve a pending request (called when user clicks Allow or Deny).
     */
    public void resolveRequest(String requestId, boolean approved) {
        CompletableFuture<Boolean> future = pendingRequests.get(requestId);
        if (future != null) {
            future.complete(approved);
            log.info("Tool consent request {} {}", requestId, approved ? "approved" : "denied");
        }
    }

    /**
     * Deny all pending requests (e.g. when user revokes control).
     */
    public void revokeAll() {
        pendingRequests.values().forEach(f -> f.complete(false));
        pendingRequests.clear();
        log.info("All pending tool consent requests revoked");
    }
}
