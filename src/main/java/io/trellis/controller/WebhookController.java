package io.trellis.controller;

import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.trellis.engine.WorkflowEngine;
import io.trellis.entity.WebhookEntity;
import io.trellis.service.WebSocketService;
import io.trellis.service.WebhookService;
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

    @RequestMapping(value = "/webhook/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD})
    public ResponseEntity<Object> handleWebhook(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        String path = request.getRequestURI().substring("/webhook/".length());
        return processWebhook(path, body, queryParams, request, false);
    }

    @RequestMapping(value = "/webhook-test/**", method = {RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD})
    public ResponseEntity<Object> handleWebhookTest(
            @RequestBody(required = false) Map<String, Object> body,
            @RequestParam Map<String, String> queryParams,
            HttpServletRequest request) {
        String path = request.getRequestURI().substring("/webhook-test/".length());
        return processWebhook(path, body, queryParams, request, true);
    }

    private ResponseEntity<Object> processWebhook(String path, Map<String, Object> body,
                                                    Map<String, String> queryParams,
                                                    HttpServletRequest request, boolean isTest) {
        String method = request.getMethod();
        Optional<WebhookEntity> webhookOpt = webhookService.resolveWebhook(method, path);

        if (webhookOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
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
            return ResponseEntity.ok(Map.of("message", "Test webhook received"));
        }

        String executionId = workflowEngine.startWebhookExecution(
                webhook.getWorkflowId(), webhook.getNodeId(), webhookData);

        return ResponseEntity.ok(Map.of("executionId", executionId, "message", "Workflow triggered"));
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
