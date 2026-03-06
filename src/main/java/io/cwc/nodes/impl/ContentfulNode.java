package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Contentful — manage content in Contentful CMS using the Delivery and Management APIs.
 */
@Node(
		type = "contentful",
		displayName = "Contentful",
		description = "Manage content in Contentful CMS",
		category = "CMS / Website Builders",
		icon = "contentful",
		credentials = {"contentfulApi"}
)
public class ContentfulNode extends AbstractApiNode {

	private static final String DELIVERY_BASE_URL = "https://cdn.contentful.com";
	private static final String MANAGEMENT_BASE_URL = "https://api.contentful.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");

		String resource = context.getParameter("resource", "entry");
		String operation = context.getParameter("operation", "getAll");
		String spaceId = context.getParameter("spaceId", "");
		String environmentId = context.getParameter("environmentId", "master");

		// Use delivery API for read operations, management API for write operations
		String baseUrl = DELIVERY_BASE_URL;

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contentType" -> handleContentType(context, headers, baseUrl, spaceId, environmentId, operation);
					case "entry" -> handleEntry(context, headers, baseUrl, spaceId, environmentId, operation);
					case "asset" -> handleAsset(context, headers, baseUrl, spaceId, environmentId, operation);
					case "locale" -> handleLocale(context, headers, baseUrl, spaceId, environmentId, operation);
					case "space" -> handleSpace(context, headers, baseUrl, spaceId, operation);
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

	private Map<String, Object> handleContentType(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String spaceId, String environmentId, String operation) throws Exception {
		String envPath = "/spaces/" + encode(spaceId) + "/environments/" + encode(environmentId);
		return switch (operation) {
			case "get" -> {
				String contentTypeId = context.getParameter("contentTypeId", "");
				HttpResponse<String> response = get(baseUrl + envPath + "/content_types/" + encode(contentTypeId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + envPath + "/content_types", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contentType operation: " + operation);
		};
	}

	private Map<String, Object> handleEntry(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String spaceId, String environmentId, String operation) throws Exception {
		String envPath = "/spaces/" + encode(spaceId) + "/environments/" + encode(environmentId);
		return switch (operation) {
			case "get" -> {
				String entryId = context.getParameter("entryId", "");
				HttpResponse<String> response = get(baseUrl + envPath + "/entries/" + encode(entryId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(baseUrl + envPath + "/entries");
				String contentTypeId = context.getParameter("contentTypeId", "");
				if (!contentTypeId.isEmpty()) {
					url.append("?content_type=").append(encode(contentTypeId));
				}
				int limit = toInt(context.getParameters().get("limit"), 100);
				String separator = url.toString().contains("?") ? "&" : "?";
				url.append(separator).append("limit=").append(limit);
				int skip = toInt(context.getParameters().get("skip"), 0);
				if (skip > 0) {
					url.append("&skip=").append(skip);
				}
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown entry operation: " + operation);
		};
	}

	private Map<String, Object> handleAsset(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String spaceId, String environmentId, String operation) throws Exception {
		String envPath = "/spaces/" + encode(spaceId) + "/environments/" + encode(environmentId);
		return switch (operation) {
			case "get" -> {
				String assetId = context.getParameter("assetId", "");
				HttpResponse<String> response = get(baseUrl + envPath + "/assets/" + encode(assetId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(baseUrl + envPath + "/assets");
				int limit = toInt(context.getParameters().get("limit"), 100);
				url.append("?limit=").append(limit);
				int skip = toInt(context.getParameters().get("skip"), 0);
				if (skip > 0) {
					url.append("&skip=").append(skip);
				}
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown asset operation: " + operation);
		};
	}

	private Map<String, Object> handleLocale(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String spaceId, String environmentId, String operation) throws Exception {
		String envPath = "/spaces/" + encode(spaceId) + "/environments/" + encode(environmentId);
		return switch (operation) {
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + envPath + "/locales", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown locale operation: " + operation);
		};
	}

	private Map<String, Object> handleSpace(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String spaceId, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(baseUrl + "/spaces/" + encode(spaceId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown space operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("entry")
						.options(List.of(
								ParameterOption.builder().name("Content Type").value("contentType").build(),
								ParameterOption.builder().name("Entry").value("entry").build(),
								ParameterOption.builder().name("Asset").value("asset").build(),
								ParameterOption.builder().name("Locale").value("locale").build(),
								ParameterOption.builder().name("Space").value("space").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("spaceId").displayName("Space ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("The ID of the Contentful space.").build(),
				NodeParameter.builder()
						.name("environmentId").displayName("Environment ID")
						.type(ParameterType.STRING).defaultValue("master")
						.description("The environment ID (defaults to 'master').").build(),
				NodeParameter.builder()
						.name("contentTypeId").displayName("Content Type ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the content type.").build(),
				NodeParameter.builder()
						.name("entryId").displayName("Entry ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the entry.").build(),
				NodeParameter.builder()
						.name("assetId").displayName("Asset ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the asset.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Maximum number of items to return.").build(),
				NodeParameter.builder()
						.name("skip").displayName("Skip")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Number of items to skip for pagination.").build()
		);
	}
}
