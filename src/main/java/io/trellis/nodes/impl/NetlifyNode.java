package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Netlify — manage sites and deploys on Netlify.
 */
@Node(
		type = "netlify",
		displayName = "Netlify",
		description = "Manage sites and deploys on Netlify",
		category = "Miscellaneous",
		icon = "netlify",
		credentials = {"netlifyApi"}
)
public class NetlifyNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.netlify.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String resource = context.getParameter("resource", "site");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "deploy" -> handleDeploy(context, headers, operation);
					case "site" -> handleSite(context, headers, operation);
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

	private Map<String, Object> handleDeploy(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String siteId = context.getParameter("siteId", "");
		return switch (operation) {
			case "cancel" -> {
				String deployId = context.getParameter("deployId", "");
				HttpResponse<String> response = post(BASE_URL + "/deploys/" + encode(deployId) + "/cancel", Map.of(), headers);
				yield parseResponse(response);
			}
			case "create" -> {
				String title = context.getParameter("title", "");
				String url = BASE_URL + "/sites/" + encode(siteId) + "/deploys";
				if (!title.isEmpty()) url += "?title=" + encode(title);
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String deployId = context.getParameter("deployId", "");
				HttpResponse<String> response = get(BASE_URL + "/sites/" + encode(siteId) + "/deploys/" + encode(deployId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/sites/" + encode(siteId) + "/deploys?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown deploy operation: " + operation);
		};
	}

	private Map<String, Object> handleSite(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "delete" -> {
				String siteId = context.getParameter("siteId", "");
				HttpResponse<String> response = delete(BASE_URL + "/sites/" + encode(siteId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String siteId = context.getParameter("siteId", "");
				HttpResponse<String> response = get(BASE_URL + "/sites/" + encode(siteId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/sites?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown site operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("site")
						.options(List.of(
								ParameterOption.builder().name("Deploy").value("deploy").build(),
								ParameterOption.builder().name("Site").value("site").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Cancel").value("cancel").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("siteId").displayName("Site ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("deployId").displayName("Deploy ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("Deploy title.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
