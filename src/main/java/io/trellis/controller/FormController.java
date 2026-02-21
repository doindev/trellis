package io.trellis.controller;

import io.trellis.engine.WorkflowEngine;
import io.trellis.entity.WaitEntity;
import io.trellis.service.WaitService;
import io.trellis.util.FormHtmlGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles form rendering and submission for Wait nodes (form/webhook modes)
 * and Form nodes (intermediate forms that pause execution).
 *
 * Endpoints:
 *   GET  /api/forms/wait/{executionId}/{nodeId}    — Render a form for a waiting node
 *   POST /api/forms/wait/{executionId}/{nodeId}    — Submit form data to resume execution
 *   GET  /api/forms/webhook/{executionId}/{nodeId} — Resume wait via webhook GET
 *   POST /api/forms/webhook/{executionId}/{nodeId} — Resume wait via webhook POST
 */
@Slf4j
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private final WaitService waitService;
    private final WorkflowEngine workflowEngine;

    /**
     * Serve the HTML form for a Wait or Form node that is waiting for user input.
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/wait/{executionId}/{nodeId}")
    public ResponseEntity<Object> showWaitForm(
            @PathVariable String executionId,
            @PathVariable String nodeId) {

        WaitEntity entry = waitService.getWaitEntry(executionId, nodeId);
        if (entry == null) {
            String html = FormHtmlGenerator.errorPage("This form is no longer available. It may have already been submitted or expired.");
            return ResponseEntity.status(404)
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }

        // Extract form definition — could be a Map with fields, or a raw List of fields
        Object formDef = entry.getFormDefinition();
        Object fields = formDef;
        String title = "Form";
        String description = null;
        String buttonLabel = "Submit";

        if (formDef instanceof Map) {
            Map<String, Object> defMap = (Map<String, Object>) formDef;
            title = (String) defMap.getOrDefault("formTitle", "Form");
            description = (String) defMap.get("formDescription");
            buttonLabel = (String) defMap.getOrDefault("buttonLabel", "Submit");
            fields = defMap.get("fields");
        }

        String postUrl = "/api/forms/wait/" + executionId + "/" + nodeId;
        String html = FormHtmlGenerator.generateForm(
                fields, postUrl, title, description, buttonLabel);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * Process form submission for a Wait or Form node — resumes workflow execution.
     */
    @PostMapping("/wait/{executionId}/{nodeId}")
    public ResponseEntity<Object> submitWaitForm(
            @PathVariable String executionId,
            @PathVariable String nodeId,
            @RequestParam Map<String, String> formData) {

        Map<String, Object> data = new LinkedHashMap<>(formData);
        boolean resumed = resumeWait(executionId, nodeId, data);

        if (resumed) {
            String html = FormHtmlGenerator.completionPage("Thank You!",
                    "Your response has been recorded successfully.");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }

        String html = FormHtmlGenerator.errorPage("This form has already been submitted or has expired.");
        return ResponseEntity.status(410)
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * Resume a Wait node via webhook GET — used by approval buttons or external systems.
     */
    @GetMapping("/webhook/{executionId}/{nodeId}")
    public ResponseEntity<Object> resumeWebhookGet(
            @PathVariable String executionId,
            @PathVariable String nodeId,
            @RequestParam Map<String, String> queryParams) {

        Map<String, Object> data = new LinkedHashMap<>(queryParams);
        boolean resumed = resumeWait(executionId, nodeId, data);

        if (resumed) {
            String html = FormHtmlGenerator.completionPage("Action Recorded",
                    "Your response has been recorded. You can close this page.");
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        }

        String html = FormHtmlGenerator.errorPage("This action has already been processed or has expired.");
        return ResponseEntity.status(410)
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }

    /**
     * Resume a Wait node via webhook POST — used by external systems posting data.
     */
    @PostMapping("/webhook/{executionId}/{nodeId}")
    public ResponseEntity<Map<String, Object>> resumeWebhookPost(
            @PathVariable String executionId,
            @PathVariable String nodeId,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> data = body != null ? body : Map.of();
        boolean resumed = resumeWait(executionId, nodeId, data);

        if (resumed) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Workflow resumed successfully"));
        }

        return ResponseEntity.status(410).body(Map.of(
                "success", false,
                "message", "Wait not found or already resumed"));
    }

    /**
     * Claims the wait from DB and triggers engine resume.
     */
    private boolean resumeWait(String executionId, String nodeId, Map<String, Object> data) {
        WaitEntity claimed = waitService.resume(executionId, nodeId);
        if (claimed == null) {
            return false;
        }

        workflowEngine.resumeFromWait(executionId, nodeId, data, claimed.getExecutionState());
        return true;
    }
}
