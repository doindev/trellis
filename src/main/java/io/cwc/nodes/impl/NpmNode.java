package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Npm — retrieve package information from the npm registry.
 */
@Node(
		type = "npm",
		displayName = "Npm",
		description = "Get package information from the npm registry",
		category = "Development / DevOps",
		icon = "npm",
		credentials = {}
)
public class NpmNode extends AbstractApiNode {

	private static final String REGISTRY_URL = "https://registry.npmjs.org";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "get");
		String packageName = context.getParameter("packageName", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "get" -> {
						HttpResponse<String> response = get(REGISTRY_URL + "/" + encode(packageName), headers);
						yield parseResponse(response);
					}
					case "getVersion" -> {
						String version = context.getParameter("version", "latest");
						HttpResponse<String> response = get(REGISTRY_URL + "/" + encode(packageName) + "/" + encode(version), headers);
						yield parseResponse(response);
					}
					case "search" -> {
						String query = context.getParameter("query", "");
						int limit = toInt(context.getParameters().get("limit"), 20);
						HttpResponse<String> response = get(REGISTRY_URL + "/-/v1/search?text=" + encode(query) + "&size=" + limit, headers);
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
						.type(ParameterType.OPTIONS)
						.defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Get Package").value("get").build(),
								ParameterOption.builder().name("Get Version").value("getVersion").build(),
								ParameterOption.builder().name("Search").value("search").build()
						)).build(),
				NodeParameter.builder()
						.name("packageName").displayName("Package Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the npm package.").build(),
				NodeParameter.builder()
						.name("version").displayName("Version")
						.type(ParameterType.STRING).defaultValue("latest")
						.description("Package version to retrieve.").build(),
				NodeParameter.builder()
						.name("query").displayName("Search Query")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search term for npm packages.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(20)
						.description("Max number of search results.").build()
		);
	}
}
