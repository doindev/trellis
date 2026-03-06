package io.cwc.nodes.impl;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * AWS SNS Trigger — starts the workflow when an SNS notification is received.
 * Handles both SubscriptionConfirmation and Notification message types.
 */
@Slf4j
@Node(
		type = "awsSnsTrigger",
		displayName = "AWS SNS Trigger",
		description = "Starts the workflow when an AWS SNS notification is received",
		category = "AWS",
		icon = "awsSns",
		trigger = true,
		credentials = {"awsApi"},
		triggerCategory = "Other"
)
public class AwsSnsTriggerNode extends AbstractApiNode {

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
						.name("topicArn").displayName("Topic ARN")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The ARN of the SNS topic to subscribe to.").build(),
				NodeParameter.builder()
						.name("triggerOn").displayName("Trigger On")
						.type(ParameterType.OPTIONS).required(true).defaultValue("notification")
						.options(List.of(
								ParameterOption.builder().name("Notification").value("notification")
										.description("Trigger on SNS notification messages").build(),
								ParameterOption.builder().name("All Messages").value("all")
										.description("Trigger on all SNS messages including subscription confirmations").build()
						)).build(),
				NodeParameter.builder()
						.name("parseMessageJson").displayName("Parse Message as JSON")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Attempt to parse the SNS message body as JSON.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String triggerOn = context.getParameter("triggerOn", "notification");
			boolean parseMessageJson = toBoolean(context.getParameters().get("parseMessageJson"), true);

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				@SuppressWarnings("unchecked")
				Map<String, Object> json = (Map<String, Object>) item.getOrDefault("json", item);

				String messageType = String.valueOf(json.getOrDefault("Type", ""));

				// Handle subscription confirmation
				if ("SubscriptionConfirmation".equals(messageType)) {
					String subscribeUrl = String.valueOf(json.getOrDefault("SubscribeURL", ""));
					if (!subscribeUrl.isEmpty()) {
						try {
							// Auto-confirm the subscription by hitting the SubscribeURL
							get(subscribeUrl, Map.of());
							log.info("AWS SNS subscription confirmed for topic: {}",
									json.getOrDefault("TopicArn", "unknown"));
						} catch (Exception e) {
							log.warn("Failed to auto-confirm SNS subscription: {}", e.getMessage());
						}
					}

					if ("all".equals(triggerOn)) {
						Map<String, Object> result = new LinkedHashMap<>(json);
						result.put("_triggerTimestamp", System.currentTimeMillis());
						result.put("_snsMessageType", messageType);
						results.add(wrapInJson(result));
					}
					continue;
				}

				// Handle notification messages
				if ("Notification".equals(messageType) || "all".equals(triggerOn)) {
					Map<String, Object> result = new LinkedHashMap<>();
					result.put("messageId", json.getOrDefault("MessageId", ""));
					result.put("topicArn", json.getOrDefault("TopicArn", ""));
					result.put("subject", json.getOrDefault("Subject", ""));
					result.put("timestamp", json.getOrDefault("Timestamp", ""));
					result.put("_snsMessageType", messageType);
					result.put("_triggerTimestamp", System.currentTimeMillis());

					String messageBody = String.valueOf(json.getOrDefault("Message", ""));
					if (parseMessageJson && !messageBody.isEmpty()) {
						try {
							Map<String, Object> parsed = parseJson(messageBody);
							if (!parsed.isEmpty()) {
								result.put("message", parsed);
							} else {
								result.put("message", messageBody);
							}
						} catch (Exception e) {
							result.put("message", messageBody);
						}
					} else {
						result.put("message", messageBody);
					}

					// Include message attributes if present
					Object messageAttributes = json.get("MessageAttributes");
					if (messageAttributes != null) {
						result.put("messageAttributes", messageAttributes);
					}

					results.add(wrapInJson(result));
				}
			}

			if (results.isEmpty()) {
				log.debug("AWS SNS trigger: no matching notifications");
				return NodeExecutionResult.empty();
			}

			log.debug("AWS SNS trigger: processing {} notifications", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "AWS SNS Trigger error: " + e.getMessage(), e);
		}
	}
}
