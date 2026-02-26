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
 * Mailchimp Trigger — starts the workflow when Mailchimp webhook events occur,
 * such as subscribe, unsubscribe, profile updates, cleaned addresses, or campaign events.
 */
@Slf4j
@Node(
		type = "mailchimpTrigger",
		displayName = "Mailchimp Trigger",
		description = "Starts the workflow when a Mailchimp event occurs",
		category = "Marketing",
		icon = "mailchimp",
		trigger = true,
		credentials = {"mailchimpOAuth2Api"}
)
public class MailchimpTriggerNode extends AbstractApiNode {

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
						.type(ParameterType.OPTIONS).required(true).defaultValue("subscribe")
						.options(List.of(
								ParameterOption.builder().name("Subscribe").value("subscribe")
										.description("Trigger when a user subscribes to a list").build(),
								ParameterOption.builder().name("Unsubscribe").value("unsubscribe")
										.description("Trigger when a user unsubscribes from a list").build(),
								ParameterOption.builder().name("Profile Update").value("profile")
										.description("Trigger when a subscriber's profile is updated").build(),
								ParameterOption.builder().name("Cleaned").value("cleaned")
										.description("Trigger when an email address is cleaned from a list").build(),
								ParameterOption.builder().name("Email Changed").value("upemail")
										.description("Trigger when a subscriber changes their email address").build(),
								ParameterOption.builder().name("Campaign Sent").value("campaign")
										.description("Trigger when a campaign is sent").build(),
								ParameterOption.builder().name("All Events").value("all")
										.description("Trigger on any Mailchimp webhook event").build()
						)).build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The Mailchimp audience/list ID to watch for events.").build(),
				NodeParameter.builder()
						.name("webhookUrl").displayName("Webhook URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("The URL Mailchimp will send events to (your workflow webhook URL).").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String triggerOn = context.getParameter("triggerOn", "subscribe");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				@SuppressWarnings("unchecked")
				Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);

				String eventType = String.valueOf(json.getOrDefault("type", "")).toLowerCase();

				boolean shouldProcess = "all".equals(triggerOn) || triggerOn.equals(eventType);

				if (shouldProcess) {
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("type", eventType);
					result.put("firedAt", json.getOrDefault("fired_at", ""));
					result.put("_triggerTimestamp", System.currentTimeMillis());

					// Extract data section
					Object data = json.get("data");
					if (data instanceof Map) {
						@SuppressWarnings("unchecked")
						Map<String, Object> dataMap = (Map<String, Object>) data;
						result.put("data", dataMap);

						// Promote commonly used fields
						if (dataMap.containsKey("email")) result.put("email", dataMap.get("email"));
						if (dataMap.containsKey("list_id")) result.put("listId", dataMap.get("list_id"));
						if (dataMap.containsKey("merges")) result.put("merges", dataMap.get("merges"));
					} else {
						result.put("data", json);
					}

					results.add(wrapInJson(result));
				}
			}

			if (results.isEmpty()) {
				log.debug("Mailchimp trigger: no matching events found");
				return NodeExecutionResult.empty();
			}

			log.debug("Mailchimp trigger: processing {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "Mailchimp Trigger error: " + e.getMessage(), e);
		}
	}
}
