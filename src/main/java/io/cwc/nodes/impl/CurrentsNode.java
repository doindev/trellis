package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Currents — fetch latest news using the Currents API.
 */
@Node(
		type = "currents",
		displayName = "Currents",
		description = "Fetch latest news from Currents API",
		category = "Miscellaneous",
		icon = "currents",
		credentials = {"currentsApi"},
		searchOnly = true
)
public class CurrentsNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.currentsapi.services/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = Map.of("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "getAll" -> {
						StringBuilder url = new StringBuilder(BASE_URL + "/latest-news?apiKey=" + encode(apiKey));
						String keywords = context.getParameter("keywords", "");
						if (!keywords.isEmpty()) url.append("&keywords=").append(encode(keywords));
						String language = context.getParameter("language", "");
						if (!language.isEmpty()) url.append("&language=").append(encode(language));
						String country = context.getParameter("country", "");
						if (!country.isEmpty()) url.append("&country=").append(encode(country));
						String category = context.getParameter("category", "");
						if (!category.isEmpty()) url.append("&category=").append(encode(category));
						HttpResponse<String> response = get(url.toString(), headers);
						yield parseResponse(response);
					}
					case "search" -> {
						StringBuilder url = new StringBuilder(BASE_URL + "/search?apiKey=" + encode(apiKey));
						String keywords = context.getParameter("keywords", "");
						if (!keywords.isEmpty()) url.append("&keywords=").append(encode(keywords));
						String language = context.getParameter("language", "");
						if (!language.isEmpty()) url.append("&language=").append(encode(language));
						String startDate = context.getParameter("startDate", "");
						if (!startDate.isEmpty()) url.append("&start_date=").append(encode(startDate));
						String endDate = context.getParameter("endDate", "");
						if (!endDate.isEmpty()) url.append("&end_date=").append(encode(endDate));
						HttpResponse<String> response = get(url.toString(), headers);
						yield parseResponse(response);
					}
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Search").value("search").build()
						)).build(),
				NodeParameter.builder()
						.name("keywords").displayName("Keywords")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING).defaultValue("")
						.description("Language code (e.g., en, de, fr).").build(),
				NodeParameter.builder()
						.name("country").displayName("Country")
						.type(ParameterType.STRING).defaultValue("")
						.description("Country code (e.g., US, GB, DE).").build(),
				NodeParameter.builder()
						.name("category").displayName("Category")
						.type(ParameterType.STRING).defaultValue("")
						.description("News category filter.").build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date for search (ISO 8601).").build(),
				NodeParameter.builder()
						.name("endDate").displayName("End Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date for search (ISO 8601).").build()
		);
	}
}
