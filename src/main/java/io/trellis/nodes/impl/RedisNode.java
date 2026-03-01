package io.trellis.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.CacheableNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import io.trellis.service.DatabaseConnectionPoolService;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

@Slf4j
@Node(
	type = "redis",
	displayName = "Redis",
	description = "Interact with a Redis database.",
	category = "Databases",
	icon = "redis",
	credentials = {"redis"}
)
public class RedisNode extends AbstractNode implements CacheableNode {

	@Autowired
	private DatabaseConnectionPoolService poolService;

	@Override
	public Map<String, List<Object>> cacheDisplayOptions() {
		return Map.of("operation", List.of("get", "keys", "info"));
	}

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
				.name("operation").displayName("Operation")
				.type(ParameterType.OPTIONS).required(true)
				.defaultValue("get")
				.options(List.of(
					ParameterOption.builder().name("Get").value("get").description("Get the value of a key").build(),
					ParameterOption.builder().name("Set").value("set").description("Set a key-value pair").build(),
					ParameterOption.builder().name("Delete").value("delete").description("Delete a key").build(),
					ParameterOption.builder().name("Push").value("push").description("Push a value to a list").build(),
					ParameterOption.builder().name("Pop").value("pop").description("Pop a value from a list").build(),
					ParameterOption.builder().name("Keys").value("keys").description("Find keys matching a pattern").build(),
					ParameterOption.builder().name("Increment").value("incr").description("Increment a numeric value").build(),
					ParameterOption.builder().name("Publish").value("publish").description("Publish a message to a channel").build(),
					ParameterOption.builder().name("Info").value("info").description("Get server info").build()
				)).build(),
			NodeParameter.builder()
				.name("key").displayName("Key")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("get", "set", "delete", "push", "pop", "incr"))))
				.build(),
			NodeParameter.builder()
				.name("value").displayName("Value")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("set", "push", "publish"))))
				.build(),
			NodeParameter.builder()
				.name("keyPattern").displayName("Key Pattern")
				.type(ParameterType.STRING).defaultValue("*")
				.displayOptions(Map.of("show", Map.of("operation", List.of("keys"))))
				.build(),
			NodeParameter.builder()
				.name("ttl").displayName("TTL (seconds)")
				.type(ParameterType.NUMBER).defaultValue(0)
				.description("Expiry time in seconds. 0 means no expiry.")
				.displayOptions(Map.of("show", Map.of("operation", List.of("set"))))
				.build(),
			NodeParameter.builder()
				.name("listDirection").displayName("Direction")
				.type(ParameterType.OPTIONS).defaultValue("left")
				.options(List.of(
					ParameterOption.builder().name("Left").value("left").build(),
					ParameterOption.builder().name("Right").value("right").build()
				))
				.displayOptions(Map.of("show", Map.of("operation", List.of("push", "pop"))))
				.build(),
			NodeParameter.builder()
				.name("channel").displayName("Channel")
				.type(ParameterType.STRING).required(true)
				.displayOptions(Map.of("show", Map.of("operation", List.of("publish"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String operation = context.getParameter("operation", "get");

		try {
			JedisPool pool = poolService.getJedisPool(credentials);
			try (Jedis jedis = pool.getResource()) {
				return switch (operation) {
					case "get" -> doGet(jedis, context);
					case "set" -> doSet(jedis, context);
					case "delete" -> doDelete(jedis, context);
					case "push" -> doPush(jedis, context);
					case "pop" -> doPop(jedis, context);
					case "keys" -> doKeys(jedis, context);
					case "incr" -> doIncr(jedis, context);
					case "publish" -> doPublish(jedis, context);
					case "info" -> doInfo(jedis, context);
					default -> NodeExecutionResult.error("Unknown operation: " + operation);
				};
			}
		} catch (Exception e) {
			return handleError(context, "Redis error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult doGet(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		String value = jedis.get(key);
		if (value == null) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "value", (Object) "null"))));
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "value", value))));
	}

	private NodeExecutionResult doSet(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		String value = context.getParameter("value", "");
		int ttl = toInt(context.getParameter("ttl", 0), 0);

		if (ttl > 0) {
			jedis.setex(key, ttl, value);
		} else {
			jedis.set(key, value);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "success", true))));
	}

	private NodeExecutionResult doDelete(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		long deleted = jedis.del(key);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "deleted", deleted))));
	}

	private NodeExecutionResult doPush(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		String value = context.getParameter("value", "");
		String direction = context.getParameter("listDirection", "left");
		long length;
		if ("right".equals(direction)) {
			length = jedis.rpush(key, value);
		} else {
			length = jedis.lpush(key, value);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "listLength", length))));
	}

	private NodeExecutionResult doPop(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		String direction = context.getParameter("listDirection", "left");
		String value;
		if ("right".equals(direction)) {
			value = jedis.rpop(key);
		} else {
			value = jedis.lpop(key);
		}
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("key", key);
		result.put("value", value);
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}

	private NodeExecutionResult doKeys(Jedis jedis, NodeExecutionContext context) {
		String pattern = context.getParameter("keyPattern", "*");
		Set<String> keys = jedis.keys(pattern);
		List<Map<String, Object>> results = new ArrayList<>();
		for (String key : keys) {
			results.add(wrapInJson(Map.of("key", key)));
		}
		return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
	}

	private NodeExecutionResult doIncr(Jedis jedis, NodeExecutionContext context) {
		String key = context.getParameter("key", "");
		long newValue = jedis.incr(key);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("key", key, "value", newValue))));
	}

	private NodeExecutionResult doPublish(Jedis jedis, NodeExecutionContext context) {
		String channel = context.getParameter("channel", "");
		String value = context.getParameter("value", "");
		long receivers = jedis.publish(channel, value);
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("channel", channel, "receivers", receivers))));
	}

	private NodeExecutionResult doInfo(Jedis jedis, NodeExecutionContext context) {
		String info = jedis.info();
		Map<String, Object> parsed = new LinkedHashMap<>();
		for (String line : info.split("\n")) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.contains(":")) {
				String[] parts = line.split(":", 2);
				String key = parts[0];
				String value = parts[1];
				if (value.contains("=")) {
					// Sub-values like "keys=10,expires=5" → nested object
					Map<String, Object> subMap = new LinkedHashMap<>();
					for (String pair : value.split(",")) {
						String[] kv = pair.split("=", 2);
						if (kv.length == 2) {
							subMap.put(kv[0], parseNumeric(kv[1]));
						}
					}
					parsed.put(key, subMap);
				} else {
					parsed.put(key, parseNumeric(value));
				}
			}
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private Object parseNumeric(String value) {
		if (value != null && value.matches("^[\\d.]+$") && !value.isEmpty()) {
			try {
				if (value.contains(".")) {
					return Double.parseDouble(value);
				}
				return Long.parseLong(value);
			} catch (NumberFormatException e) {
				// fall through
			}
		}
		return value;
	}
}
