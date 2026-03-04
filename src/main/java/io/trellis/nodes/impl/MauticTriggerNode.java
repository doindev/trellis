package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Mautic Trigger — starts the workflow when a Mautic webhook event is received
 * (contact events, form submissions, page hits, point changes, etc.).
 */
@Slf4j
@Node(
		type = "mauticTrigger",
		displayName = "Mautic Trigger",
		description = "Starts the workflow when a Mautic event occurs",
		category = "Marketing",
		icon = "mautic",
		trigger = true,
		credentials = {"mauticApi"},
		searchOnly = true,
		other = true
)
public class MauticTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("triggerOn").displayName("Trigger On")
						.type(ParameterType.OPTIONS).required(true).defaultValue("contactUpdated")
						.options(List.of(
								ParameterOption.builder().name("Contact Created").value("contactCreated")
										.description("Trigger when a new contact is created").build(),
								ParameterOption.builder().name("Contact Updated").value("contactUpdated")
										.description("Trigger when a contact is updated").build(),
								ParameterOption.builder().name("Contact Deleted").value("contactDeleted")
										.description("Trigger when a contact is deleted").build(),
								ParameterOption.builder().name("Contact Identified").value("contactIdentified")
										.description("Trigger when a contact is identified").build(),
								ParameterOption.builder().name("Form Submitted").value("formSubmit")
										.description("Trigger when a form is submitted").build(),
								ParameterOption.builder().name("Page Hit").value("pageHit")
										.description("Trigger when a page is visited").build(),
								ParameterOption.builder().name("Point Change").value("pointChange")
										.description("Trigger when a contact's points change").build(),
								ParameterOption.builder().name("Email Open").value("emailOpen")
										.description("Trigger when an email is opened").build(),
								ParameterOption.builder().name("All Events").value("all")
										.description("Trigger on any Mautic webhook event").build()
						)).build(),
				NodeParameter.builder()
						.name("formId").displayName("Form ID (Filter)")
						.type(ParameterType.STRING).defaultValue("")
						.description("Only trigger for submissions of this form ID. Leave empty for all forms.")
						.displayOptions(Map.of("show", Map.of("triggerOn", List.of("formSubmit")))).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String triggerOn = context.getParameter("triggerOn", "contactUpdated");
			String formIdFilter = context.getParameter("formId", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				@SuppressWarnings("unchecked")
				Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);

				// Mautic webhooks contain event name keys like "mautic.lead_post_save_new"
				// The webhook payload varies based on configuration
				String webhookTrigger = String.valueOf(json.getOrDefault("mautic.webhook_trigger", ""));

				boolean shouldProcess = switch (triggerOn) {
					case "contactCreated" -> webhookTrigger.contains("lead_post_save_new") ||
							json.containsKey("mautic.lead_post_save_new");
					case "contactUpdated" -> webhookTrigger.contains("lead_post_save_update") ||
							json.containsKey("mautic.lead_post_save_update");
					case "contactDeleted" -> webhookTrigger.contains("lead_post_delete") ||
							json.containsKey("mautic.lead_post_delete");
					case "contactIdentified" -> webhookTrigger.contains("lead_post_identified") ||
							json.containsKey("mautic.lead_post_identified");
					case "formSubmit" -> {
						boolean isFormSubmit = webhookTrigger.contains("form_on_submit") ||
								json.containsKey("mautic.form_on_submit");
						if (isFormSubmit && !formIdFilter.isEmpty()) {
							@SuppressWarnings("unchecked")
							Map<String, Object> submission = (Map<String, Object>) json.getOrDefault("mautic.form_on_submit", Map.of());
							@SuppressWarnings("unchecked")
							Map<String, Object> form = (Map<String, Object>) submission.getOrDefault("form", Map.of());
							String formId = String.valueOf(form.getOrDefault("id", ""));
							yield formIdFilter.equals(formId);
						}
						yield isFormSubmit;
					}
					case "pageHit" -> webhookTrigger.contains("page_on_hit") ||
							json.containsKey("mautic.page_on_hit");
					case "pointChange" -> webhookTrigger.contains("lead_points_change") ||
							json.containsKey("mautic.lead_points_change");
					case "emailOpen" -> webhookTrigger.contains("email_on_open") ||
							json.containsKey("mautic.email_on_open");
					case "all" -> true;
					default -> true;
				};

				if (shouldProcess) {
					Map<String, Object> result = new LinkedHashMap<>(json);
					result.put("_triggerTimestamp", System.currentTimeMillis());
					result.put("_mauticEvent", triggerOn);
					results.add(wrapInJson(result));
				}
			}

			if (results.isEmpty()) {
				log.debug("Mautic trigger: no matching events found");
				return NodeExecutionResult.empty();
			}

			log.debug("Mautic trigger: processing {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "Mautic Trigger error: " + e.getMessage(), e);
		}
	}
}
