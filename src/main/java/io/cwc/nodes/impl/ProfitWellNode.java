package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * ProfitWell — retrieve company settings and subscription metrics from ProfitWell.
 */
@Node(
		type = "profitWell",
		displayName = "ProfitWell",
		description = "Get company settings and subscription metrics from ProfitWell",
		category = "Finance",
		icon = "profitWell",
		credentials = {"profitWellApi"},
		searchOnly = true
)
public class ProfitWellNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.profitwell.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "metric");
		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", apiToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "company" -> {
						HttpResponse<String> response = get(BASE_URL + "/company/settings/", headers);
						yield parseResponse(response);
					}
					case "metric" -> {
						String type = context.getParameter("type", "monthly");
						String url = BASE_URL + "/metrics/" + type;
						StringBuilder params = new StringBuilder();
						String month = context.getParameter("month", "");
						if (!month.isEmpty() && "daily".equals(type)) {
							params.append("month=").append(encode(month));
						}
						String metrics = context.getParameter("metrics", "");
						if (!metrics.isEmpty()) {
							if (!params.isEmpty()) params.append("&");
							params.append("metrics=").append(encode(metrics));
						}
						String planId = context.getParameter("planId", "");
						if (!planId.isEmpty()) {
							if (!params.isEmpty()) params.append("&");
							params.append("plan_id=").append(encode(planId));
						}
						if (!params.isEmpty()) {
							url += "?" + params;
						}
						HttpResponse<String> response = get(url, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("metric")
						.options(List.of(
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Metric").value("metric").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get Settings").value("getSettings").build(),
								ParameterOption.builder().name("Get").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("type").displayName("Type")
						.type(ParameterType.OPTIONS).defaultValue("monthly")
						.options(List.of(
								ParameterOption.builder().name("Daily").value("daily").build(),
								ParameterOption.builder().name("Monthly").value("monthly").build()
						)).build(),
				NodeParameter.builder()
						.name("month").displayName("Month")
						.type(ParameterType.STRING).defaultValue("")
						.description("Month to query for daily metrics (YYYY-MM format).").build(),
				NodeParameter.builder()
						.name("metrics").displayName("Metrics")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of metric names to include.").build(),
				NodeParameter.builder()
						.name("planId").displayName("Plan ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Optional plan ID to filter metrics.").build()
		);
	}
}
