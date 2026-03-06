package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Oura — retrieve health and activity data from the Oura Ring API.
 */
@Node(
		type = "oura",
		displayName = "Oura",
		description = "Get health and activity data from Oura Ring",
		category = "Miscellaneous",
		icon = "oura",
		credentials = {"ouraApi"},
		searchOnly = true
)
public class OuraNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.ouraring.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "profile");
		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "profile" -> {
						HttpResponse<String> response = get(BASE_URL + "/usercollection/personal_info", headers);
						yield parseResponse(response);
					}
					case "summary" -> {
						String startDate = context.getParameter("startDate", "");
						String endDate = context.getParameter("endDate", "");
						String endpoint = switch (operation) {
							case "getActivity" -> "/usercollection/daily_activity";
							case "getReadiness" -> "/usercollection/daily_readiness";
							case "getSleep" -> "/usercollection/daily_sleep";
							default -> throw new IllegalArgumentException("Unknown summary operation: " + operation);
						};
						StringBuilder url = new StringBuilder(BASE_URL + endpoint);
						String sep = "?";
						if (!startDate.isEmpty()) { url.append(sep).append("start_date=").append(encode(startDate)); sep = "&"; }
						if (!endDate.isEmpty()) { url.append(sep).append("end_date=").append(encode(endDate)); }
						HttpResponse<String> response = get(url.toString(), headers);
						yield parseResponse(response);
					}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("profile")
						.options(List.of(
								ParameterOption.builder().name("Profile").value("profile").build(),
								ParameterOption.builder().name("Summary").value("summary").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getActivity")
						.options(List.of(
								ParameterOption.builder().name("Get Activity").value("getActivity").build(),
								ParameterOption.builder().name("Get Readiness").value("getReadiness").build(),
								ParameterOption.builder().name("Get Sleep").value("getSleep").build()
						)).build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("endDate").displayName("End Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date in YYYY-MM-DD format.").build()
		);
	}
}
