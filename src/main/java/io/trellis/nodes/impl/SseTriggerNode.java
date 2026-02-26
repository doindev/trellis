package io.trellis.nodes.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * SSE Trigger Node -- connects to a Server-Sent Events endpoint and outputs
 * received events. In polling mode, it stores the last event ID in staticData
 * and uses it for reconnection to avoid duplicate events.
 */
@Slf4j
@Node(
	type = "sseTrigger",
	displayName = "SSE Trigger",
	description = "Connects to a Server-Sent Events (SSE) endpoint and triggers on new events.",
	category = "Core / Triggers",
	icon = "sseTrigger",
	trigger = true,
	polling = true,
	credentials = {"httpHeaderAuth"}
)
public class SseTriggerNode extends AbstractTriggerNode {

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(30))
		.followRedirects(HttpClient.Redirect.NORMAL)
		.build();

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		params.add(NodeParameter.builder()
			.name("url").displayName("SSE URL")
			.type(ParameterType.STRING).required(true)
			.placeHolder("https://example.com/events")
			.description("The URL of the Server-Sent Events endpoint.")
			.build());

		params.add(NodeParameter.builder()
			.name("headers").displayName("Additional Headers")
			.type(ParameterType.COLLECTION)
			.description("Additional HTTP headers to send with the request.")
			.nestedParameters(List.of(
				NodeParameter.builder().name("headerName").displayName("Header Name")
					.type(ParameterType.STRING).placeHolder("X-Custom-Header").build(),
				NodeParameter.builder().name("headerValue").displayName("Header Value")
					.type(ParameterType.STRING).placeHolder("value").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("maxEvents").displayName("Max Events")
			.type(ParameterType.NUMBER).defaultValue(100)
			.description("Maximum number of events to return per poll. The connection will be closed after this many events.")
			.build());

		params.add(NodeParameter.builder()
			.name("timeout").displayName("Connection Timeout (seconds)")
			.type(ParameterType.NUMBER).defaultValue(30)
			.description("Maximum time to wait for events before closing the connection.")
			.build());

		params.add(NodeParameter.builder()
			.name("eventFilter").displayName("Event Type Filter")
			.type(ParameterType.STRING).defaultValue("")
			.description("Only output events of this type. Leave empty for all event types.")
			.placeHolder("message")
			.build());

		return params;
	}

	// ========================= Execute =========================

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String url = context.getParameter("url", "");
		int maxEvents = toInt(context.getParameter("maxEvents", 100), 100);
		int timeout = toInt(context.getParameter("timeout", 30), 30);
		String eventFilter = context.getParameter("eventFilter", "");
		Map<String, Object> credentials = context.getCredentials();

		if (url.isBlank()) {
			return NodeExecutionResult.error("SSE URL is required.");
		}

		try {
			// Get last event ID from static data
			Map<String, Object> staticData = context.getStaticData();
			if (staticData == null) {
				staticData = new HashMap<>();
			}
			String lastEventId = (String) staticData.getOrDefault("lastEventId", "");

			// Build HTTP request
			HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.GET()
				.timeout(Duration.ofSeconds(timeout))
				.header("Accept", "text/event-stream")
				.header("Cache-Control", "no-cache")
				.header("User-Agent", "Trellis/1.0");

			// Add Last-Event-ID if we have one
			if (!lastEventId.isEmpty()) {
				requestBuilder.header("Last-Event-ID", lastEventId);
			}

			// Add credentials headers
			if (credentials != null) {
				String headerName = String.valueOf(credentials.getOrDefault("name", credentials.getOrDefault("headerName", "")));
				String headerValue = String.valueOf(credentials.getOrDefault("value", credentials.getOrDefault("headerValue", "")));
				if (!headerName.isEmpty() && !headerValue.isEmpty()) {
					requestBuilder.header(headerName, headerValue);
				}
			}

			// Add additional headers
			Map<String, Object> headers = context.getParameter("headers", Map.of());
			String headerName = String.valueOf(headers.getOrDefault("headerName", ""));
			String headerValue = String.valueOf(headers.getOrDefault("headerValue", ""));
			if (!headerName.isEmpty() && !headerValue.isEmpty()) {
				requestBuilder.header(headerName, headerValue);
			}

			HttpResponse<java.io.InputStream> response = httpClient.send(
				requestBuilder.build(),
				HttpResponse.BodyHandlers.ofInputStream()
			);

			if (response.statusCode() >= 400) {
				return NodeExecutionResult.error("SSE connection failed (HTTP " + response.statusCode() + ")");
			}

			// Parse SSE stream
			List<Map<String, Object>> results = new ArrayList<>();
			String newLastEventId = lastEventId;

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
				String currentEventType = "";
				String currentEventId = "";
				StringBuilder currentData = new StringBuilder();
				int eventCount = 0;

				String line;
				long startTime = System.currentTimeMillis();
				long timeoutMs = timeout * 1000L;

				while ((line = reader.readLine()) != null && eventCount < maxEvents) {
					// Check timeout
					if (System.currentTimeMillis() - startTime > timeoutMs) {
						break;
					}

					if (line.isEmpty()) {
						// Empty line means end of event
						if (currentData.length() > 0) {
							// Apply event type filter
							if (eventFilter.isEmpty() || eventFilter.equals(currentEventType)) {
								Map<String, Object> eventItem = new LinkedHashMap<>();
								eventItem.put("event", currentEventType.isEmpty() ? "message" : currentEventType);
								eventItem.put("data", currentData.toString().trim());
								eventItem.put("id", currentEventId);
								eventItem.put("url", url);
								eventItem.put("timestamp", System.currentTimeMillis());

								// Try to parse data as JSON
								String dataStr = currentData.toString().trim();
								if (dataStr.startsWith("{") || dataStr.startsWith("[")) {
									try {
										eventItem.put("parsedData", dataStr);
									} catch (Exception ignored) {
										// Keep raw string
									}
								}

								results.add(createTriggerItem(eventItem));
								eventCount++;
							}

							// Update last event ID
							if (!currentEventId.isEmpty()) {
								newLastEventId = currentEventId;
							}
						}

						// Reset for next event
						currentEventType = "";
						currentEventId = "";
						currentData = new StringBuilder();
					} else if (line.startsWith("data:")) {
						String data = line.substring(5).trim();
						if (currentData.length() > 0) {
							currentData.append("\n");
						}
						currentData.append(data);
					} else if (line.startsWith("event:")) {
						currentEventType = line.substring(6).trim();
					} else if (line.startsWith("id:")) {
						currentEventId = line.substring(3).trim();
					} else if (line.startsWith("retry:")) {
						// Retry directive -- could be stored but not needed for polling
						log.debug("SSE retry directive: {}", line.substring(6).trim());
					}
					// Lines starting with ':' are comments -- ignore
				}
			}

			// Save updated static data
			Map<String, Object> updatedStaticData = new HashMap<>(staticData);
			updatedStaticData.put("lastEventId", newLastEventId);
			updatedStaticData.put("lastPollTimestamp", System.currentTimeMillis());

			if (results.isEmpty()) {
				return NodeExecutionResult.builder()
					.output(List.of(List.of()))
					.staticData(updatedStaticData)
					.build();
			}

			return NodeExecutionResult.builder()
				.output(List.of(results))
				.staticData(updatedStaticData)
				.build();

		} catch (Exception e) {
			return handleError(context, "SSE Trigger error: " + e.getMessage(), e);
		}
	}
}
