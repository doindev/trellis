package io.trellis.controller;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.trellis.engine.WorkflowEngine;
import io.trellis.entity.WebhookEntity;
import io.trellis.service.WebSocketService;
import io.trellis.service.WebhookService;
import io.trellis.util.FormHtmlGenerator;
import org.springframework.http.MediaType;
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
    private final ObjectMapper objectMapper;

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

        // Parse webhook options
        Map<String, Object> options = parseWebhookOptions(webhook.getWebhookOptions());

        // Check ignoreBots — reject requests from known bots/crawlers
        if (Boolean.TRUE.equals(options.get("ignoreBots"))) {
            String userAgent = request.getHeader("User-Agent");
            if (userAgent != null && isBot(userAgent)) {
                log.debug("Webhook rejected bot request: UA={}", userAgent);
                deferredResult.setResult(ResponseEntity.status(403)
                        .body(Map.of("error", "Bot requests are not allowed")));
                return deferredResult;
            }
        }

        // Check IP allowlist
        String ipWhitelist = (String) options.getOrDefault("ipWhitelist", "");
        if (ipWhitelist != null && !ipWhitelist.isBlank()) {
            String clientIp = getClientIp(request);
            if (!isIpAllowed(clientIp, ipWhitelist)) {
                log.debug("Webhook rejected IP: {} not in allowlist {}", clientIp, ipWhitelist);
                deferredResult.setResult(ResponseEntity.status(403)
                        .body(Map.of("error", "IP address not allowed")));
                return deferredResult;
            }
        }

        // Handle form trigger webhooks — serve form on GET, process submission on POST
        if ("formTrigger".equals(webhook.getResponseMode())) {
            return processFormTrigger(webhook, method, body, queryParams, request, isTest, deferredResult, options);
        }

        Map<String, Object> webhookData = new LinkedHashMap<>();
        webhookData.put("headers", extractHeaders(request));
        webhookData.put("params", queryParams);
        webhookData.put("method", method);
        webhookData.put("path", path);

        // rawBody option — store body as JSON string instead of parsed object
        if (Boolean.TRUE.equals(options.get("rawBody"))) {
            try {
                String rawBodyStr = body != null ? objectMapper.writeValueAsString(body) : "";
                webhookData.put("body", rawBodyStr);
            } catch (Exception e) {
                webhookData.put("body", body != null ? body : Map.of());
            }
        } else {
            webhookData.put("body", body != null ? body : Map.of());
        }

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

            int responseCode = 200;
            Object rcObj = options.get("responseCode");
            if (rcObj instanceof Number) {
                responseCode = ((Number) rcObj).intValue();
            }

            boolean noResponseBody = Boolean.TRUE.equals(options.get("noResponseBody"));
            if (noResponseBody) {
                deferredResult.setResult(ResponseEntity.status(responseCode).build());
            } else {
                Object customResponseData = options.get("responseData");
                if (customResponseData != null && !String.valueOf(customResponseData).isBlank()) {
                    deferredResult.setResult(ResponseEntity.status(responseCode)
                            .body(customResponseData));
                } else {
                    deferredResult.setResult(ResponseEntity.status(responseCode)
                            .body(Map.of("executionId", executionId, "message", "Workflow triggered")));
                }
            }
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

    private DeferredResult<ResponseEntity<Object>> processFormTrigger(
            WebhookEntity webhook, String method, Map<String, Object> body,
            Map<String, String> queryParams, HttpServletRequest request,
            boolean isTest, DeferredResult<ResponseEntity<Object>> deferredResult,
            Map<String, Object> options) {

        if ("GET".equalsIgnoreCase(method)) {
            // Serve the HTML form
            String formTitle = (String) options.getOrDefault("formTitle", "Form");
            String formDescription = (String) options.getOrDefault("formDescription", "");
            Object formFields = options.get("formFields");
            String buttonLabel = (String) options.getOrDefault("buttonLabel", "Submit");

            // Extract field definitions from FIXED_COLLECTION format
            List<Map<String, Object>> fields = extractFormFields(formFields);

            String postUrl = request.getRequestURI();
            String html = FormHtmlGenerator.generateForm(
                    fields, postUrl, formTitle, formDescription, buttonLabel);

            deferredResult.setResult(ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html));
        } else if ("POST".equalsIgnoreCase(method)) {
            // Process form submission — merge form data with query params
            Map<String, Object> formData = new LinkedHashMap<>();
            if (queryParams != null) formData.putAll(queryParams);
            if (body != null) formData.putAll(body);

            if (isTest) {
                webSocketService.sendWebhookTestData(webhook.getWorkflowId(), formData);
                cancelTimeout(webhook.getWorkflowId());
                webhookService.deregisterTestWebhooks(webhook.getWorkflowId());
                String html = FormHtmlGenerator.completionPage("Test Received", "Form data was captured for testing.");
                deferredResult.setResult(ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html));
            } else {
                // Trigger workflow with form data
                Map<String, Object> webhookData = new LinkedHashMap<>();
                webhookData.put("formData", formData);
                webhookData.put("method", method);
                webhookData.put("path", webhook.getPath());
                webhookData.put("submittedAt", java.time.Instant.now().toString());

                workflowEngine.startWebhookExecution(
                        webhook.getWorkflowId(), webhook.getNodeId(), webhookData);

                String html = FormHtmlGenerator.completionPage("Thank You!",
                        "Your response has been submitted successfully.");
                deferredResult.setResult(ResponseEntity.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(html));
            }
        } else {
            deferredResult.setResult(ResponseEntity.status(405)
                    .body(Map.of("error", "Method not allowed for form trigger")));
        }

        return deferredResult;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractFormFields(Object formFields) {
        if (formFields instanceof List) {
            return (List<Map<String, Object>>) formFields;
        }
        if (formFields instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) formFields;
            Object values = map.get("values");
            if (values instanceof List) {
                return (List<Map<String, Object>>) values;
            }
        }
        return List.of();
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseWebhookOptions(Object optionsObj) {
        if (optionsObj instanceof Map) {
            return (Map<String, Object>) optionsObj;
        }
        return Map.of();
    }

    private static final Pattern BOT_PATTERN = Pattern.compile(
        "(?i)(bot|crawl|spider|slurp|bingpreview|mediapartners|google|facebookexternalhit"
        + "|linkedinbot|twitterbot|whatsapp|telegrambot|applebot|yandex|baiduspider"
        + "|duckduckbot|semrushbot|ahrefsbot|dotbot|petalbot|mj12bot|sogou)",
        Pattern.CASE_INSENSITIVE);

    private boolean isBot(String userAgent) {
        return BOT_PATTERN.matcher(userAgent).find();
    }

    private String getClientIp(HttpServletRequest request) {
        // Check common proxy headers
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For can contain multiple IPs; first is the original client
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isIpAllowed(String clientIp, String whitelist) {
        if (whitelist == null || whitelist.isBlank()) return true;

        String[] entries = whitelist.split(",");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.contains("/")) {
                // CIDR notation
                if (isIpInCidr(clientIp, trimmed)) return true;
            } else {
                // Exact IP match
                if (trimmed.equals(clientIp)) return true;
            }
        }
        return false;
    }

    private boolean isIpInCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            InetAddress network = InetAddress.getByName(parts[0].trim());
            int prefixLength = Integer.parseInt(parts[1].trim());

            byte[] networkBytes = network.getAddress();
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();

            if (networkBytes.length != ipBytes.length) return false;

            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;

            for (int i = 0; i < fullBytes; i++) {
                if (networkBytes[i] != ipBytes[i]) return false;
            }

            if (remainingBits > 0 && fullBytes < networkBytes.length) {
                int mask = 0xFF << (8 - remainingBits);
                if ((networkBytes[fullBytes] & mask) != (ipBytes[fullBytes] & mask)) return false;
            }

            return true;
        } catch (Exception e) {
            log.warn("Failed to parse CIDR {}: {}", cidr, e.getMessage());
            return false;
        }
    }
}
