package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Brevo Trigger — starts the workflow when a Brevo webhook event is received
 * (transactional email events, contact changes, etc.).
 */
@Slf4j
@Node(
		type = "brevoTrigger",
		displayName = "Brevo Trigger",
		description = "Starts the workflow when a Brevo event occurs",
		category = "Communication / Email",
		icon = "brevo",
		trigger = true,
		credentials = {"brevoApi"},
		searchOnly = true,
		triggerCategory = "Other"
)
public class BrevoTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.brevo.com/v3";

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
						.type(ParameterType.OPTIONS).required(true).defaultValue("transactionalEmail")
						.options(List.of(
								ParameterOption.builder().name("Transactional Email Event").value("transactionalEmail")
										.description("Trigger on email delivery events (delivered, opened, clicked, etc.)").build(),
								ParameterOption.builder().name("Contact Event").value("contact")
										.description("Trigger when contacts are created, updated, or deleted").build(),
								ParameterOption.builder().name("Inbound Email").value("inboundEmail")
										.description("Trigger when an inbound email is received").build(),
								ParameterOption.builder().name("All Events").value("all")
										.description("Trigger on any Brevo webhook event").build()
						)).build(),
				NodeParameter.builder()
						.name("events").displayName("Email Events (Filter)")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated event types to filter: request, delivered, opened, clicked, hardBounce, softBounce, spam, invalid, deferred, unsubscribed, blocked, error.")
						.displayOptions(Map.of("show", Map.of("triggerOn", List.of("transactionalEmail")))).build(),
				NodeParameter.builder()
						.name("webhookUrl").displayName("Webhook URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("The URL Brevo will send events to (your workflow webhook URL).").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String triggerOn = context.getParameter("triggerOn", "transactionalEmail");
		String eventsFilter = context.getParameter("events", "");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received — filter and pass through
				List<Map<String, Object>> results = new ArrayList<>();

				Set<String> allowedEvents = new HashSet<>();
				if (!eventsFilter.isEmpty()) {
					for (String event : eventsFilter.split("\\s*,\\s*")) {
						allowedEvents.add(event.trim().toLowerCase());
					}
				}

				for (Map<String, Object> item : inputData) {
					@SuppressWarnings("unchecked")
					Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);
					String eventType = String.valueOf(json.getOrDefault("event", "")).toLowerCase();

					boolean shouldProcess = switch (triggerOn) {
						case "transactionalEmail" -> {
							boolean isEmailEvent = List.of("request", "delivered", "opened", "clicked",
									"hardbounce", "softbounce", "spam", "invalid", "deferred",
									"unsubscribed", "blocked", "error").contains(eventType);
							yield isEmailEvent && (allowedEvents.isEmpty() || allowedEvents.contains(eventType));
						}
						case "contact" -> eventType.startsWith("contact");
						case "inboundEmail" -> "inbound".equals(eventType) || eventType.startsWith("inbound");
						case "all" -> true;
						default -> true;
					};

					if (shouldProcess) {
						Map<String, Object> result = new LinkedHashMap<>(json);
						result.put("_triggerEvent", triggerOn);
						result.put("_triggerTimestamp", System.currentTimeMillis());
						results.add(wrapInJson(result));
					}
				}

				if (results.isEmpty()) {
					log.debug("Brevo trigger: no matching events found");
					return NodeExecutionResult.empty();
				}

				log.debug("Brevo trigger: processing {} events", results.size());
				return NodeExecutionResult.success(results);
			}

			// No webhook data — attempt to register webhook with Brevo
			Map<String, Object> credentials = context.getCredentials();
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty() && !apiKey.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("api-key", apiKey);
				headers.put("Content-Type", "application/json");

				List<String> events = resolveWebhookEvents(triggerOn, eventsFilter);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("url", webhookUrl);
				body.put("description", "CWC workflow trigger");
				body.put("events", events);

				HttpResponse<String> response = post(BASE_URL + "/webhooks", body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Brevo webhook: {}", response.body());
				} else {
					log.debug("Brevo webhook registered for events: {}", events);
				}
			}

			// Return trigger metadata
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", triggerOn);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Brevo Trigger error: " + e.getMessage(), e);
		}
	}

	private List<String> resolveWebhookEvents(String triggerOn, String eventsFilter) {
		return switch (triggerOn) {
			case "transactionalEmail" -> {
				if (!eventsFilter.isEmpty()) {
					List<String> filtered = new ArrayList<>();
					for (String event : eventsFilter.split("\\s*,\\s*")) {
						filtered.add(event.trim());
					}
					yield filtered;
				}
				yield List.of("request", "delivered", "opened", "clicked",
						"hardBounce", "softBounce", "spam", "invalid", "deferred",
						"unsubscribed", "blocked", "error");
			}
			case "contact" -> List.of("contactUpdated", "contactDeleted");
			case "inboundEmail" -> List.of("inboundEmailProcessed");
			default -> List.of("request", "delivered", "opened", "clicked",
					"hardBounce", "softBounce", "spam", "invalid", "deferred",
					"unsubscribed", "blocked", "error",
					"contactUpdated", "contactDeleted", "inboundEmailProcessed");
		};
	}
}
