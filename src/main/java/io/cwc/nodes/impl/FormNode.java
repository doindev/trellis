package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * Form Node — shows an intermediate form page within a workflow, pausing execution
 * until the user submits the form.
 *
 * Two operations:
 *   - Page: Shows a form, waits for submission via DB checkpoint/resume.
 *   - Completion: Shows a completion message (no further input needed).
 */
@Slf4j
@Node(
    type = "form",
    displayName = "Form",
    description = "Shows a form to collect human input mid-workflow. Execution pauses until the form is submitted.",
    category = "Human in the Loop",
    icon = "file-input",
    implementationNotes = "Execution pauses at this node and generates a form URL at /api/forms/wait/{executionId}/{nodeId}. " +
        "The workflow resumes when the form is submitted. Use 'limitWaitTime' to auto-resume after a timeout. " +
        "Form field values are available in the output as $json['Field Label']. Use 'completion' operation " +
        "as the last node in a multi-page form flow to show a thank-you message or redirect."
)
public class FormNode extends AbstractNode {

    private static final List<Map<String, Object>> FIELD_TYPE_OPTIONS = List.of(
        Map.of("name", "Text", "value", "text"),
        Map.of("name", "Number", "value", "number"),
        Map.of("name", "Email", "value", "email"),
        Map.of("name", "Password", "value", "password"),
        Map.of("name", "Textarea", "value", "textarea"),
        Map.of("name", "Dropdown", "value", "dropdown"),
        Map.of("name", "Checkbox", "value", "checkbox"),
        Map.of("name", "Radio", "value", "radio"),
        Map.of("name", "Date", "value", "date"),
        Map.of("name", "Hidden", "value", "hidden")
    );

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
            // Operation
            NodeParameter.builder()
                .name("operation")
                .displayName("Operation")
                .description("What this form node does.")
                .type(ParameterType.OPTIONS)
                .defaultValue("page")
                .required(true)
                .options(List.of(
                    Map.of("name", "Form Page", "value", "page",
                        "description", "Show a form and wait for the user to submit it"),
                    Map.of("name", "Completion", "value", "completion",
                        "description", "Show a completion message (end of form flow)")
                ))
                .build(),

            // ---- Page parameters ----
            NodeParameter.builder()
                .name("formTitle")
                .displayName("Form Title")
                .description("Title displayed at the top of the form.")
                .type(ParameterType.STRING)
                .defaultValue("Form")
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .build(),

            NodeParameter.builder()
                .name("formDescription")
                .displayName("Form Description")
                .description("Description shown below the form title.")
                .type(ParameterType.STRING)
                .typeOptions(Map.of("rows", 3))
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .build(),

            NodeParameter.builder()
                .name("formFields")
                .displayName("Form Fields")
                .description("Define the fields displayed on the form.")
                .type(ParameterType.FIXED_COLLECTION)
                .defaultValue(List.of())
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .nestedParameters(List.of(
                    NodeParameter.builder()
                        .name("fieldLabel")
                        .displayName("Label")
                        .description("Display label for the field.")
                        .type(ParameterType.STRING)
                        .required(true)
                        .placeHolder("e.g. Approval Notes")
                        .build(),
                    NodeParameter.builder()
                        .name("fieldType")
                        .displayName("Field Type")
                        .description("The type of form input.")
                        .type(ParameterType.OPTIONS)
                        .defaultValue("text")
                        .options(FIELD_TYPE_OPTIONS)
                        .build(),
                    NodeParameter.builder()
                        .name("placeholder")
                        .displayName("Placeholder")
                        .description("Placeholder text shown in the input.")
                        .type(ParameterType.STRING)
                        .build(),
                    NodeParameter.builder()
                        .name("defaultValue")
                        .displayName("Default Value")
                        .description("Pre-filled value for the field.")
                        .type(ParameterType.STRING)
                        .build(),
                    NodeParameter.builder()
                        .name("required")
                        .displayName("Required")
                        .description("Whether this field must be filled in.")
                        .type(ParameterType.BOOLEAN)
                        .defaultValue(false)
                        .build(),
                    NodeParameter.builder()
                        .name("selectOptions")
                        .displayName("Options")
                        .description("Comma-separated list of options (for dropdown, checkbox, radio).")
                        .type(ParameterType.STRING)
                        .placeHolder("Option A, Option B, Option C")
                        .build()
                ))
                .build(),

            NodeParameter.builder()
                .name("buttonLabel")
                .displayName("Submit Button Label")
                .description("Text displayed on the submit button.")
                .type(ParameterType.STRING)
                .defaultValue("Submit")
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .build(),

            // Limit wait time for form
            NodeParameter.builder()
                .name("limitWaitTime")
                .displayName("Limit Wait Time")
                .description("Set a maximum time to wait for form submission.")
                .type(ParameterType.BOOLEAN)
                .defaultValue(false)
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .build(),

            NodeParameter.builder()
                .name("maxWaitAmount")
                .displayName("Max Wait")
                .description("Maximum time to wait before auto-resuming.")
                .type(ParameterType.NUMBER)
                .defaultValue(60)
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"),
                    "limitWaitTime", List.of(true))))
                .build(),

            NodeParameter.builder()
                .name("maxWaitUnit")
                .displayName("Max Wait Unit")
                .description("Time unit for the maximum wait.")
                .type(ParameterType.OPTIONS)
                .defaultValue("minutes")
                .options(List.of(
                    Map.of("name", "Minutes", "value", "minutes"),
                    Map.of("name", "Hours", "value", "hours"),
                    Map.of("name", "Days", "value", "days")
                ))
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"),
                    "limitWaitTime", List.of(true))))
                .build(),

            // Notice about form URL
            NodeParameter.builder()
                .name("formUrlNotice")
                .displayName("")
                .description("When this node runs, it will pause execution and generate a form URL at: "
                    + "<code>/api/forms/wait/{executionId}/{nodeId}</code>")
                .type(ParameterType.NOTICE)
                .noDataExpression(true)
                .displayOptions(Map.of("show", Map.of("operation", List.of("page"))))
                .build(),

            // ---- Completion parameters ----
            NodeParameter.builder()
                .name("respondWith")
                .displayName("Respond With")
                .description("What to show when the form flow is complete.")
                .type(ParameterType.OPTIONS)
                .defaultValue("text")
                .options(List.of(
                    Map.of("name", "Show Message", "value", "text",
                        "description", "Display a completion message"),
                    Map.of("name", "Redirect", "value", "redirect",
                        "description", "Redirect to a URL")
                ))
                .displayOptions(Map.of("show", Map.of("operation", List.of("completion"))))
                .build(),

            NodeParameter.builder()
                .name("completionTitle")
                .displayName("Completion Title")
                .description("Title shown on the completion page.")
                .type(ParameterType.STRING)
                .defaultValue("Thank You!")
                .displayOptions(Map.of("show", Map.of("operation", List.of("completion"),
                    "respondWith", List.of("text"))))
                .build(),

            NodeParameter.builder()
                .name("completionMessage")
                .displayName("Completion Message")
                .description("Message shown on the completion page.")
                .type(ParameterType.STRING)
                .defaultValue("Your submission has been received.")
                .typeOptions(Map.of("rows", 3))
                .displayOptions(Map.of("show", Map.of("operation", List.of("completion"),
                    "respondWith", List.of("text"))))
                .build(),

            NodeParameter.builder()
                .name("redirectUrl")
                .displayName("Redirect URL")
                .description("URL to redirect to after completion.")
                .type(ParameterType.STRING)
                .placeHolder("https://example.com/thanks")
                .displayOptions(Map.of("show", Map.of("operation", List.of("completion"),
                    "respondWith", List.of("redirect"))))
                .build()
        );
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        String operation = context.getParameter("operation", "page");

        return switch (operation) {
            case "page" -> handlePage(context);
            case "completion" -> handleCompletion(context);
            default -> NodeExecutionResult.error("Unknown operation: " + operation);
        };
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult handlePage(NodeExecutionContext context) {
        String executionId = context.getExecutionId();
        String nodeId = context.getNodeId();
        String formTitle = context.getParameter("formTitle", "Form");
        String formDescription = context.getParameter("formDescription", "");
        String buttonLabel = context.getParameter("buttonLabel", "Submit");

        // Extract form fields from parameters
        Object formFieldsParam = context.getParameters().get("formFields");
        List<Map<String, Object>> fields = new ArrayList<>();
        if (formFieldsParam instanceof List) {
            fields = (List<Map<String, Object>>) formFieldsParam;
        } else if (formFieldsParam instanceof Map) {
            Object values = ((Map<String, Object>) formFieldsParam).get("values");
            if (values instanceof List) {
                fields = (List<Map<String, Object>>) values;
            }
        }

        // Build form definition to persist in the WaitEntity
        Map<String, Object> formDef = new LinkedHashMap<>();
        formDef.put("formTitle", formTitle);
        formDef.put("formDescription", formDescription);
        formDef.put("buttonLabel", buttonLabel);
        formDef.put("fields", fields);

        log.info("Form node waiting for submission — execution {}, node {}. "
                + "Form URL: /api/forms/wait/{}/{}",
                executionId, nodeId, executionId, nodeId);

        // Calculate optional timeout resumeAt
        Instant resumeAt = null;
        boolean limitWait = context.getParameter("limitWaitTime", false);
        if (limitWait) {
            int maxAmount = context.getParameter("maxWaitAmount", 60);
            String maxUnit = context.getParameter("maxWaitUnit", "minutes");
            resumeAt = Instant.now().plus(calculateDuration(maxAmount, maxUnit));
        }

        return NodeExecutionResult.waiting(NodeExecutionResult.WaitConfig.builder()
                .waitType("form")
                .formDefinition(formDef)
                .resumeAt(resumeAt)
                .build());
    }

    private NodeExecutionResult handleCompletion(NodeExecutionContext context) {
        log.info("Form completion node — execution {}", context.getExecutionId());
        return NodeExecutionResult.success(context.getInputData());
    }

    private Duration calculateDuration(int amount, String unit) {
        return switch (unit) {
            case "minutes" -> Duration.ofMinutes(amount);
            case "hours" -> Duration.ofHours(amount);
            case "days" -> Duration.ofDays(amount);
            default -> Duration.ofMinutes(amount);
        };
    }
}
