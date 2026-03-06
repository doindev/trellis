package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Form Trigger Node — starts a workflow when a web form is submitted.
 * Registers a form URL at /webhook/{path} which serves an HTML form on GET
 * and triggers the workflow on POST.
 *
 * Supports configurable form fields (text, number, email, password, textarea,
 * dropdown, checkbox, radio, date, hidden).
 */
@Slf4j
@Node(
    type = "formTrigger",
    displayName = "On Form Submission",
    description = "Starts the workflow when a user submits a web form at the configured URL.",
    category = "Human in the Loop",
    icon = "clipboard-list",
    trigger = true,
    triggerFavorite = true
)
public class FormTriggerNode extends AbstractTriggerNode {

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
    public List<NodeParameter> getParameters() {
        return List.of(
            // Path
            NodeParameter.builder()
                .name("path")
                .displayName("Form Path")
                .description("The URL path for the form. Access at: <code>/webhook/{path}</code>")
                .type(ParameterType.STRING)
                .required(true)
                .placeHolder("my-form")
                .build(),

            // Form Title
            NodeParameter.builder()
                .name("formTitle")
                .displayName("Form Title")
                .description("Title displayed at the top of the form.")
                .type(ParameterType.STRING)
                .defaultValue("Submit Form")
                .build(),

            // Form Description
            NodeParameter.builder()
                .name("formDescription")
                .displayName("Form Description")
                .description("Description shown below the form title.")
                .type(ParameterType.STRING)
                .typeOptions(Map.of("rows", 3))
                .build(),

            // Form Fields (FIXED_COLLECTION)
            NodeParameter.builder()
                .name("formFields")
                .displayName("Form Fields")
                .description("Define the fields displayed on the form.")
                .type(ParameterType.FIXED_COLLECTION)
                .defaultValue(List.of())
                .nestedParameters(List.of(
                    NodeParameter.builder()
                        .name("fieldLabel")
                        .displayName("Label")
                        .description("Display label for the field.")
                        .type(ParameterType.STRING)
                        .required(true)
                        .placeHolder("e.g. Full Name")
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

            // Button Label
            NodeParameter.builder()
                .name("buttonLabel")
                .displayName("Submit Button Label")
                .description("Text displayed on the submit button.")
                .type(ParameterType.STRING)
                .defaultValue("Submit")
                .build(),

            // Authentication
            NodeParameter.builder()
                .name("authentication")
                .displayName("Authentication")
                .description("Authentication method for accessing the form.")
                .type(ParameterType.OPTIONS)
                .defaultValue("none")
                .options(List.of(
                    Map.of("name", "None", "value", "none")
                ))
                .build(),

            // Response Mode
            NodeParameter.builder()
                .name("responseMode")
                .displayName("Respond")
                .description("When to respond to the form submission.")
                .type(ParameterType.OPTIONS)
                .defaultValue("onReceived")
                .options(List.of(
                    Map.of("name", "Immediately", "value", "onReceived",
                        "description", "Show a thank you page immediately after submission"),
                    Map.of("name", "When Last Node Finishes", "value", "lastNode",
                        "description", "Wait for the workflow to complete before responding")
                ))
                .build()
        );
    }

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        String path = context.getParameter("path", "");

        log.debug("Form trigger executed: path={}, workflow={}",
                path, context.getWorkflowId());

        List<Map<String, Object>> inputData = context.getInputData();

        if (inputData != null && !inputData.isEmpty()) {
            // Form data received via webhook — enrich with metadata
            List<Map<String, Object>> outputItems = new ArrayList<>();
            for (Map<String, Object> item : inputData) {
                Map<String, Object> enriched = deepClone(item);
                Map<String, Object> json = unwrapJson(enriched);

                // If form data is nested under "formData", flatten it
                Object formDataObj = json.get("formData");
                if (formDataObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> formData = (Map<String, Object>) formDataObj;
                    json.putAll(formData);
                    json.remove("formData");
                }

                json.put("_formPath", path);
                json.put("_formTimestamp", Instant.now().toString());
                outputItems.add(wrapInJson(json));
            }
            return NodeExecutionResult.success(outputItems);
        }

        // No input data — produce trigger item
        Map<String, Object> triggerData = new HashMap<>();
        triggerData.put("_formPath", path);
        triggerData.put("_formTimestamp", Instant.now().toString());
        return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));
    }
}
