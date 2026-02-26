package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Demio — manage webinar events and registrations using the Demio API.
 */
@Node(
		type = "demio",
		displayName = "Demio",
		description = "Manage webinars and registrations using Demio",
		category = "Miscellaneous",
		icon = "demio",
		credentials = {"demioApi"}
)
public class DemioNode extends AbstractApiNode {

	private static final String BASE_URL = "https://my.demio.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String apiSecret = (String) credentials.getOrDefault("apiSecret", "");

		String resource = context.getParameter("resource", "event");
		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Api-Key", apiKey);
		headers.put("Api-Secret", apiSecret);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "event" -> handleEvent(context, headers, operation);
					case "report" -> handleReport(context, headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleEvent(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String eventId = context.getParameter("eventId", "");
				String dateId = context.getParameter("dateId", "");
				String url = BASE_URL + "/event/" + encode(eventId);
				if (!dateId.isEmpty()) url += "/date/" + encode(dateId);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/events", headers);
				yield parseResponse(response);
			}
			case "register" -> {
				String eventId = context.getParameter("eventId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", eventId);
				body.put("name", context.getParameter("firstName", ""));
				body.put("email", context.getParameter("email", ""));
				String dateId = context.getParameter("dateId", "");
				if (!dateId.isEmpty()) body.put("date_id", dateId);
				HttpResponse<String> response = put(BASE_URL + "/event/register", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown event operation: " + operation);
		};
	}

	private Map<String, Object> handleReport(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String sessionId = context.getParameter("sessionId", "");
		HttpResponse<String> response = get(BASE_URL + "/report/" + encode(sessionId) + "/participants", headers);
		return parseResponse(response);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("event")
						.options(List.of(
								ParameterOption.builder().name("Event").value("event").build(),
								ParameterOption.builder().name("Report").value("report").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Register").value("register").build()
						)).build(),
				NodeParameter.builder()
						.name("eventId").displayName("Event ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dateId").displayName("Date/Session ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Specific session date ID.").build(),
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Session ID for report data.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
