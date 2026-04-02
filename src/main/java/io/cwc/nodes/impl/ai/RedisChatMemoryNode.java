package io.cwc.nodes.impl.ai;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractAiMemoryNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import io.cwc.nodes.impl.ai.memory.ChatMessageSerialization;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.List;

@Node(
		type = "memoryRedisChat",
		displayName = "Redis Chat Memory",
		description = "Stores chat history in Redis with optional TTL",
		category = "AI / Memory",
		icon = "database",
		credentials = "redis",
		searchOnly = true
)
public class RedisChatMemoryNode extends AbstractAiMemoryNode {

	private static final String KEY_PREFIX = "chat_memory:";

	@Override
	public Object supplyData(NodeExecutionContext context) {
		int sessionTTL = toInt(context.getParameters().get("sessionTTL"), 0);

		// Build Redis connection from credentials
		String host = context.getCredentialString("host", "localhost");
		int port = toInt(context.getCredentials().get("port"), 6379);
		String password = context.getCredentialString("password");
		int database = toInt(context.getCredentials().get("database"), 0);
		boolean ssl = Boolean.parseBoolean(context.getCredentialString("ssl", "false"));

		JedisPoolConfig poolConfig = new JedisPoolConfig();
		JedisPool pool;
		if (password != null && !password.isBlank()) {
			pool = new JedisPool(poolConfig, host, port, 2000, password, database, ssl);
		} else {
			pool = new JedisPool(poolConfig, host, port, 2000, null, database, ssl);
		}

		return new RedisChatMemoryStore(pool, sessionTTL);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("sessionTTL").displayName("Session TTL (seconds)")
						.type(ParameterType.NUMBER)
						.defaultValue(0)
						.description("Time-to-live for the session in seconds. 0 means no expiration.")
						.build()
		);
	}

	/**
	 * ChatMemoryStore backed by Redis.
	 * Messages are stored as a JSON string under key "chat_memory:{sessionId}".
	 * Supports optional TTL per session.
	 */
	static class RedisChatMemoryStore implements ChatMemoryStore {
		private final JedisPool pool;
		private final int ttlSeconds;

		RedisChatMemoryStore(JedisPool pool, int ttlSeconds) {
			this.pool = pool;
			this.ttlSeconds = ttlSeconds;
		}

		@Override
		public List<ChatMessage> getMessages(Object memoryId) {
			try (Jedis jedis = pool.getResource()) {
				String json = jedis.get(KEY_PREFIX + memoryId.toString());
				if (json == null) return List.of();
				return ChatMessageSerialization.deserialize(json);
			}
		}

		@Override
		public void updateMessages(Object memoryId, List<ChatMessage> messages) {
			String json = ChatMessageSerialization.serialize(messages);
			String key = KEY_PREFIX + memoryId.toString();
			try (Jedis jedis = pool.getResource()) {
				if (ttlSeconds > 0) {
					jedis.setex(key, ttlSeconds, json);
				} else {
					jedis.set(key, json);
				}
			}
		}

		@Override
		public void deleteMessages(Object memoryId) {
			try (Jedis jedis = pool.getResource()) {
				jedis.del(KEY_PREFIX + memoryId.toString());
			}
		}
	}
}
