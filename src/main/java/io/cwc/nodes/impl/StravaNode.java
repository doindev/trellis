package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Strava — manage activities using the Strava API.
 */
@Node(
		type = "strava",
		displayName = "Strava",
		description = "Manage activities using Strava",
		category = "Miscellaneous",
		icon = "strava",
		credentials = {"stravaOAuth2Api"},
		searchOnly = true
)
public class StravaNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.strava.com/api/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "activity");
		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "activity" -> handleActivity(context, headers, operation);
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

	private Map<String, Object> handleActivity(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("name", ""));
				body.put("type", context.getParameter("activityType", "Run"));
				body.put("start_date_local", context.getParameter("startDate", ""));
				body.put("elapsed_time", toInt(context.getParameters().get("elapsedTime"), 0));
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				double distance = toDouble(context.getParameters().get("distance"), 0.0);
				if (distance > 0) body.put("distance", distance);
				HttpResponse<String> response = post(BASE_URL + "/activities", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String activityId = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/activities/" + encode(activityId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 30);
				HttpResponse<String> response = get(BASE_URL + "/athlete/activities?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "getComments" -> {
				String activityId = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/activities/" + encode(activityId) + "/comments", headers);
				yield parseResponse(response);
			}
			case "getKudos" -> {
				String activityId = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/activities/" + encode(activityId) + "/kudos", headers);
				yield parseResponse(response);
			}
			case "getLaps" -> {
				String activityId = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/activities/" + encode(activityId) + "/laps", headers);
				yield parseResponse(response);
			}
			case "getZones" -> {
				String activityId = context.getParameter("activityId", "");
				HttpResponse<String> response = get(BASE_URL + "/activities/" + encode(activityId) + "/zones", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String activityId = context.getParameter("activityId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String activityType = context.getParameter("activityType", "");
				if (!activityType.isEmpty()) body.put("type", activityType);
				HttpResponse<String> response = put(BASE_URL + "/activities/" + encode(activityId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown activity operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("activity")
						.options(List.of(
								ParameterOption.builder().name("Activity").value("activity").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Comments").value("getComments").build(),
								ParameterOption.builder().name("Get Kudos").value("getKudos").build(),
								ParameterOption.builder().name("Get Laps").value("getLaps").build(),
								ParameterOption.builder().name("Get Zones").value("getZones").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("activityId").displayName("Activity ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("activityType").displayName("Activity Type")
						.type(ParameterType.OPTIONS).defaultValue("Run")
						.options(List.of(
								ParameterOption.builder().name("Ride").value("Ride").build(),
								ParameterOption.builder().name("Run").value("Run").build(),
								ParameterOption.builder().name("Swim").value("Swim").build(),
								ParameterOption.builder().name("Walk").value("Walk").build(),
								ParameterOption.builder().name("Hike").value("Hike").build(),
								ParameterOption.builder().name("Workout").value("Workout").build()
						)).build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("ISO 8601 formatted date time.").build(),
				NodeParameter.builder()
						.name("elapsedTime").displayName("Elapsed Time (seconds)")
						.type(ParameterType.NUMBER).defaultValue(0).build(),
				NodeParameter.builder()
						.name("distance").displayName("Distance (meters)")
						.type(ParameterType.NUMBER).defaultValue(0).build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(30)
						.description("Max activities to return.").build()
		);
	}
}
