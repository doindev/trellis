package io.trellis.controller;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import io.trellis.engine.WorkflowEngine;
import io.trellis.entity.WebhookEntity;
import io.trellis.service.WebSocketService;
import io.trellis.service.WebhookService;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;
    private final WorkflowEngine workflowEngine;
    private final WebSocketService webSocketService;

    private static final long TEST_WEBHOOK_TIMEOUT_MS = 120_000; // 2 minutes
    private static final long WEBHOOK_RESPONSE_TIMEOUT_MS = 30_000; // 30 seconds
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> timeoutHandles = new ConcurrentHashMap<>();

    @RequestMapping(value = "/webhook/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD})
    public DeferredResult<ResponseEntity<Object>> handleWebhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        String path = request.getRequestURI().substring("/webhook/".length());
        return processWebhook(path, body, queryParams, request, false);
    }

    @RequestMapping(value = "/webhook-test/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD})
    public DeferredResult<ResponseEntity<Object>> handleWebhookTest(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        String path = request.getRequestURI().substring("/webhook-test/".length());
        return processWebhook(path, body, queryParams, request, true);
    }

    @PostMapping("/api/webhooks/test/listen")
    public ResponseEntity<Object> startListening(@RequestBody Map<String, String> body) {
        String workflowId = body.get("workflowId");
        String nodeId = body.get("nodeId");
        String method = body.getOrDefault("method", "GET");
        String path = body.getOrDefault("path", "");

        // Clean up any prior test webhooks for this workflow
        cancelTimeout(workflowId);
        webhookService.deregisterTestWebhooks(workflowId);

        // Register the test webhook
        WebhookEntity webhook = webhookService.registerTestWebhook(workflowId, nodeId, method, path);

        // Schedule a 2-minute timeout
        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            timeoutHandles.remove(workflowId);
            webhookService.deregisterTestWebhooks(workflowId);
            webSocketService.sendWebhookTestData(workflowId, Map.of(
                    "event", "testWebhookTimeout",
                    "workflowId", workflowId
            ));
            log.info("Test webhook timed out for workflow {}", workflowId);
        }, TEST_WEBHOOK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        timeoutHandles.put(workflowId, timeout);

        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String testUrl = "/webhook-test/" + normalizedPath;

        log.info("Started listening for test webhook: {} {} for workflow {}", method, path, workflowId);
        return ResponseEntity.ok(Map.of(
                "listening", true,
                "testUrl", testUrl,
                "webhookId", webhook.getId()
        ));
    }

    @DeleteMapping("/api/webhooks/test/{workflowId}")
    public ResponseEntity<Object> stopListening(@PathVariable String workflowId) {
        cancelTimeout(workflowId);
        webhookService.deregisterTestWebhooks(workflowId);
        webSocketService.sendWebhookTestData(workflowId, Map.of(
                "event", "testWebhookCancelled",
                "workflowId", workflowId
        ));
        log.info("Stopped listening for test webhook for workflow {}", workflowId);
        return ResponseEntity.ok(Map.of("cancelled", true));
    }

    private DeferredResult<ResponseEntity<Object>> processWebhook(String path, Map<String, Object> body,
                                                    Map<String, String> queryParams,
                                                    HttpServletRequest request, boolean isTest) {
        DeferredResult<ResponseEntity<Object>> deferredResult = new DeferredResult<>(WEBHOOK_RESPONSE_TIMEOUT_MS);
        deferredResult.onTimeout(() -> deferredResult.setResult(
                ResponseEntity.status(504).body(Map.of("error", "Webhook response timeout"))));

        String method = request.getMethod();
        Optional<WebhookEntity> webhookOpt = webhookService.resolveWebhook(method, path, isTest);

        if (webhookOpt.isEmpty()) {
            deferredResult.setResult(ResponseEntity.notFound().build());
            return deferredResult;
        }

        WebhookEntity webhook = webhookOpt.get();

        Map<String, Object> webhookData = new LinkedHashMap<>();
        webhookData.put("headers", extractHeaders(request));
        webhookData.put("params", queryParams);
        webhookData.put("body", body != null ? body : Map.of());
        webhookData.put("method", method);
        webhookData.put("path", path);

        if (isTest) {
            webSocketService.sendWebhookTestData(webhook.getWorkflowId(), webhookData);

            // One-shot: clean up test webhook after receiving data
            cancelTimeout(webhook.getWorkflowId());
            webhookService.deregisterTestWebhooks(webhook.getWorkflowId());

            deferredResult.setResult(ResponseEntity.ok(Map.of("message", "Test webhook received")));
            return deferredResult;
        }

        String responseMode = webhook.getResponseMode();

        if ("onReceived".equals(responseMode)) {
            // Fire and forget — return immediately
            String executionId = workflowEngine.startWebhookExecution(
                    webhook.getWorkflowId(), webhook.getNodeId(), webhookData);
            deferredResult.setResult(ResponseEntity.ok(
                    Map.of("executionId", executionId, "message", "Workflow triggered")));
        } else {
            // responseNode or lastNode — wait for workflow to produce response
            CompletableFuture<Map<String, Object>> future = workflowEngine.startWebhookExecutionWithResponse(
                    webhook.getWorkflowId(), webhook.getNodeId(), webhookData);

            future.whenComplete((response, ex) -> {
                if (ex != null) {
                    log.error("Webhook workflow execution failed", ex);
                    deferredResult.setResult(ResponseEntity.status(500).body(
                            Map.of("error", "Workflow execution failed: " + ex.getMessage())));
                } else {
                    deferredResult.setResult(buildWebhookResponse(response));
                }
            });
        }

        return deferredResult;
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> buildWebhookResponse(Map<String, Object> response) {
        int statusCode = 200;
        Object statusObj = response.get("statusCode");
        if (statusObj instanceof Number) {
            statusCode = ((Number) statusObj).intValue();
        }

        Object body = response.getOrDefault("body", Map.of());

        var builder = ResponseEntity.status(statusCode);

        Object headersObj = response.get("headers");
        if (headersObj instanceof Map) {
            ((Map<String, Object>) headersObj).forEach((key, value) -> {
                if (value != null) {
                    builder.header(key, value.toString());
                }
            });
        }

        return builder.body(body);
    }

    private void cancelTimeout(String workflowId) {
        ScheduledFuture<?> future = timeoutHandles.remove(workflowId);
        if (future != null) {
            future.cancel(false);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }
}
