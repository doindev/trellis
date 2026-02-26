package io.trellis.nodes.impl;

import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Redis Trigger Node -- trigger-based node that subscribes to Redis channels
 * or monitors Redis keys for changes. When used in webhook mode, it processes
 * incoming Redis pub/sub messages.
 */
@Slf4j
@Node(
	type = "redisTrigger",
	displayName = "Redis Trigger",
	description = "Starts the workflow when messages are received on a Redis channel or key changes are detected",
	category = "Database",
	icon = "redis",
	trigger = true,
	credentials = {"redisApi"}
)
public class RedisTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("message")
				.options(List.of(
					ParameterOption.builder().name("Channel Message").value("message")
						.description("Trigger when a message is received on a Redis channel").build(),
					ParameterOption.builder().name("Key Expiration").value("keyExpiration")
						.description("Trigger when a Redis key expires").build(),
					ParameterOption.builder().name("Key Set").value("keySet")
						.description("Trigger when a Redis key is set").build(),
					ParameterOption.builder().name("Key Deleted").value("keyDeleted")
						.description("Trigger when a Redis key is deleted").build(),
					ParameterOption.builder().name("List Push").value("listPush")
						.description("Trigger when an item is pushed to a Redis list").build()
				)).build(),

			NodeParameter.builder()
				.name("channels").displayName("Channels")
				.type(ParameterType.STRING)
				.description("Comma-separated list of Redis channels to subscribe to.")
				.placeHolder("channel1, channel2")
				.displayOptions(Map.of("show", Map.of("event", List.of("message"))))
				.build(),

			NodeParameter.builder()
				.name("keyPattern").displayName("Key Pattern")
				.type(ParameterType.STRING)
				.description("Redis key pattern to monitor (supports glob patterns).")
				.placeHolder("user:*")
				.displayOptions(Map.of("show", Map.of("event", List.of("keyExpiration", "keySet", "keyDeleted"))))
				.build(),

			NodeParameter.builder()
				.name("listKey").displayName("List Key")
				.type(ParameterType.STRING)
				.description("The Redis list key to monitor for new items.")
				.displayOptions(Map.of("show", Map.of("event", List.of("listPush"))))
				.build(),

			NodeParameter.builder()
				.name("database").displayName("Database")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("The Redis database number (0-15).")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		@SuppressWarnings("unused")
		Map<String, Object> credentials = context.getCredentials();
		String event = context.getParameter("event", "message");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Data received (from pub/sub, keyspace notification, or list pop)
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					Map<String, Object> enriched = new LinkedHashMap<>(unwrapJson(item));
					enriched.put("_triggerEvent", event);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());

					switch (event) {
						case "message" -> {
							String channels = context.getParameter("channels", "");
							enriched.put("_triggerChannels", channels);
						}
						case "keyExpiration", "keySet", "keyDeleted" -> {
							String keyPattern = context.getParameter("keyPattern", "");
							enriched.put("_triggerKeyPattern", keyPattern);
						}
						case "listPush" -> {
							String listKey = context.getParameter("listKey", "");
							enriched.put("_triggerListKey", listKey);
						}
					}

					results.add(wrapInJson(enriched));
				}
				return NodeExecutionResult.success(results);
			}

			// No data received -- return trigger placeholder
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());

			switch (event) {
				case "message" -> {
					String channels = context.getParameter("channels", "");
					triggerData.put("_triggerChannels", channels);
					log.debug("Redis trigger configured for channels: {}", channels);
				}
				case "keyExpiration", "keySet", "keyDeleted" -> {
					String keyPattern = context.getParameter("keyPattern", "");
					triggerData.put("_triggerKeyPattern", keyPattern);
					log.debug("Redis trigger configured for keyspace event '{}' with pattern: {}", event, keyPattern);
				}
				case "listPush" -> {
					String listKey = context.getParameter("listKey", "");
					triggerData.put("_triggerListKey", listKey);
					log.debug("Redis trigger configured for list push on key: {}", listKey);
				}
			}

			return NodeExecutionResult.success(List.of(createTriggerItem(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Redis Trigger error: " + e.getMessage(), e);
		}
	}
}
