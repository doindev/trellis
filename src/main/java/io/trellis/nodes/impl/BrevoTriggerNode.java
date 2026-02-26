package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Brevo Trigger — starts the workflow when a Brevo webhook event is received
 * (transactional email events, contact changes, etc.).
 */
@Slf4j
@Node(
		type = "brevoTrigger",
		displayName = "Brevo Trigger",
		description = "Starts the workflow when a Brevo event occurs",
		category = "Email",
		icon = "brevo",
		trigger = true,
		credentials = {"brevoApi"}
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
		try {
			String triggerOn = context.getParameter("triggerOn", "transactionalEmail");
			String eventsFilter = context.getParameter("events", "");

			List<Map<String, Object>> inputData = context.getInputData();
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
		} catch (Exception e) {
			return handleError(context, "Brevo Trigger error: " + e.getMessage(), e);
		}
	}
}
