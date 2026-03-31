package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Wait Node — pauses workflow execution.
 * Supports three resume modes:
 *   - After Time Interval: waits a fixed duration (via DB poller)
 *   - At Specified Time: waits until a target datetime (via DB poller)
 *   - On Webhook Call: pauses until an external HTTP call resumes it
 *
 * Returns NodeExecutionResult.waiting() to checkpoint state to DB and release the thread.
 */
@Slf4j
@Node(
    type = "wait",
    displayName = "Wait",
    description = "Pauses the workflow execution for a specified time or until resumed by a webhook call.",
    category = "Human in the Loop",
    icon = "timer",
    implementationNotes = "Three resume modes: 'timeInterval' (wait N seconds/minutes/hours/days), 'specificTime' " +
        "(resume at ISO 8601 datetime), or 'webhook' (pause until an HTTP call to /api/forms/webhook/{executionId}/{nodeId}). " +
        "In webhook mode, use 'limitWaitTime' to auto-resume after a timeout. The webhook resume URL is generated " +
        "at runtime and available in the execution data."
)
public class WaitNode extends AbstractNode {

    @Override
    public List<NodeInput> getInputs() {
        return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
    }

    @Override
    public List<NodeOutput> getOutputs() {
        return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
    }

    @Override
    public List<NodeParameter> getParameters() {
        return List.of(
            NodeParameter.builder()
                .name("resume")
                .displayName("Resume")
                .description("When to resume execution.")
                .type(ParameterType.OPTIONS)
                .defaultValue("timeInterval")
                .required(true)
                .options(List.of(
                    Map.of("name", "After Time Interval", "value", "timeInterval"),
                    Map.of("name", "At Specified Time", "value", "specificTime"),
                    Map.of("name", "On Webhook Call", "value", "webhook")
                ))
                .build(),

            // ---- Time Interval parameters ----
            NodeParameter.builder()
                .name("amount")
                .displayName("Wait Amount")
                .description("How long to wait.")
                .type(ParameterType.NUMBER)
                .defaultValue(1)
                .displayOptions(Map.of("show", Map.of("resume", List.of("timeInterval"))))
                .build(),

            NodeParameter.builder()
                .name("unit")
                .displayName("Wait Unit")
                .description("Time unit for the wait duration.")
                .type(ParameterType.OPTIONS)
                .defaultValue("seconds")
                .options(List.of(
                    Map.of("name", "Seconds", "value", "seconds"),
                    Map.of("name", "Minutes", "value", "minutes"),
                    Map.of("name", "Hours", "value", "hours"),
                    Map.of("name", "Days", "value", "days")
                ))
                .displayOptions(Map.of("show", Map.of("resume", List.of("timeInterval"))))
                .build(),

            // ---- Specific Time parameter ----
            NodeParameter.builder()
                .name("dateTime")
                .displayName("Date & Time")
                .description("Resume at this date and time (ISO 8601 format, e.g. 2025-12-31T14:30:00Z).")
                .type(ParameterType.STRING)
                .placeHolder("2025-12-31T14:30:00Z")
                .displayOptions(Map.of("show", Map.of("resume", List.of("specificTime"))))
                .build(),

            // ---- Webhook parameters ----
            NodeParameter.builder()
                .name("webhookNotice")
                .displayName("")
                .description("The workflow will pause and wait for an HTTP call to resume it. "
                    + "The resume URL will be: <code>/api/forms/webhook/{executionId}/{nodeId}</code>")
                .type(ParameterType.NOTICE)
                .noDataExpression(true)
                .displayOptions(Map.of("show", Map.of("resume", List.of("webhook"))))
                .build(),

            NodeParameter.builder()
                .name("httpMethod")
                .displayName("HTTP Method")
                .description("The HTTP method for the resume webhook.")
                .type(ParameterType.OPTIONS)
                .defaultValue("GET")
                .options(List.of(
                    Map.of("name", "GET", "value", "GET"),
                    Map.of("name", "POST", "value", "POST")
                ))
                .displayOptions(Map.of("show", Map.of("resume", List.of("webhook"))))
                .build(),

            // ---- Limit Wait Time (for webhook mode) ----
            NodeParameter.builder()
                .name("limitWaitTime")
                .displayName("Limit Wait Time")
                .description("Set a maximum time to wait before automatically resuming.")
                .type(ParameterType.BOOLEAN)
                .defaultValue(false)
                .displayOptions(Map.of("show", Map.of("resume", List.of("webhook"))))
                .build(),

            NodeParameter.builder()
                .name("maxWaitAmount")
                .displayName("Max Wait")
                .description("Maximum time to wait before auto-resuming.")
                .type(ParameterType.NUMBER)
                .defaultValue(60)
                .displayOptions(Map.of("show", Map.of("resume", List.of("webhook"),
                    "limitWaitTime", List.of(true))))
                .build(),

            NodeParameter.builder()
                .name("maxWaitUnit")
                .displayName("Max Wait Unit")
                .description("Time unit for the maximum wait.")
                .type(ParameterType.OPTIONS)
                .defaultValue("minutes")
                .options(List.of(
                    Map.of("name", "Seconds", "value", "seconds"),
                    Map.of("name", "Minutes", "value", "minutes"),
                    Map.of("name", "Hours", "value", "hours")
                ))
                .displayOptions(Map.of("show", Map.of("resume", List.of("webhook"),
                    "limitWaitTime", List.of(true))))
                .build()
        );
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        String resume = context.getParameter("resume", "timeInterval");

        return switch (resume) {
            case "timeInterval" -> handleTimeInterval(context);
            case "specificTime" -> handleSpecificTime(context);
            case "webhook" -> handleWebhook(context);
            default -> NodeExecutionResult.error("Unknown resume mode: " + resume);
        };
    }

    private NodeExecutionResult handleTimeInterval(NodeExecutionContext context) {
        int amount = context.getParameter("amount", 1);
        String unit = context.getParameter("unit", "seconds");
        Instant resumeAt = Instant.now().plus(calculateDuration(amount, unit));

        log.info("Wait node scheduling resume at {} — execution {}",
                resumeAt, context.getExecutionId());

        return NodeExecutionResult.waiting(NodeExecutionResult.WaitConfig.builder()
                .waitType("timeInterval")
                .resumeAt(resumeAt)
                .build());
    }

    private NodeExecutionResult handleSpecificTime(NodeExecutionContext context) {
        String dateTimeStr = context.getParameter("dateTime", "");
        if (dateTimeStr.isEmpty()) {
            return NodeExecutionResult.error("No date/time specified");
        }

        Instant target;
        try {
            target = Instant.parse(dateTimeStr);
        } catch (Exception e) {
            return NodeExecutionResult.error("Invalid date/time format: " + dateTimeStr
                + ". Use ISO 8601 format (e.g., 2025-12-31T14:30:00Z).");
        }

        if (target.isBefore(Instant.now())) {
            log.info("Wait node: target time {} already passed, continuing immediately", dateTimeStr);
            return NodeExecutionResult.success(context.getInputData());
        }

        log.info("Wait node scheduling resume at {} — execution {}",
                dateTimeStr, context.getExecutionId());

        return NodeExecutionResult.waiting(NodeExecutionResult.WaitConfig.builder()
                .waitType("specificTime")
                .resumeAt(target)
                .build());
    }

    private NodeExecutionResult handleWebhook(NodeExecutionContext context) {
        String executionId = context.getExecutionId();
        String nodeId = context.getNodeId();

        log.info("Wait node entering webhook wait — execution {}, node {}. "
                + "Resume URL: /api/forms/webhook/{}/{}",
                executionId, nodeId, executionId, nodeId);

        // If limitWaitTime is set, calculate a resumeAt for the poller to auto-resume
        Instant resumeAt = null;
        boolean limitWait = context.getParameter("limitWaitTime", false);
        if (limitWait) {
            int maxAmount = context.getParameter("maxWaitAmount", 60);
            String maxUnit = context.getParameter("maxWaitUnit", "minutes");
            resumeAt = Instant.now().plus(calculateDuration(maxAmount, maxUnit));
        }

        return NodeExecutionResult.waiting(NodeExecutionResult.WaitConfig.builder()
                .waitType("webhook")
                .resumeAt(resumeAt)
                .build());
    }

    private Duration calculateDuration(int amount, String unit) {
        return switch (unit) {
            case "seconds" -> Duration.ofSeconds(amount);
            case "minutes" -> Duration.ofMinutes(amount);
            case "hours" -> Duration.ofHours(amount);
            case "days" -> Duration.ofDays(amount);
            default -> Duration.ofSeconds(amount);
        };
    }
}
