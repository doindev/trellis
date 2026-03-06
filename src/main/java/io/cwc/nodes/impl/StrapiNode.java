package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Strapi — CRUD operations on entries in Strapi CMS via its REST API.
 */
@Node(
		type = "strapi",
		displayName = "Strapi",
		description = "Create, read, update and delete entries in Strapi CMS",
		category = "CMS / Website Builders",
		icon = "strapi",
		credentials = {"strapiApi"}
)
public class StrapiNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "");
		String apiToken = context.getCredentialString("apiToken", "");
		String contentType = context.getParameter("contentType", "");
		String operation = context.getParameter("operation", "getMany");
		String apiVersion = context.getParameter("apiVersion", "v4");

		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		// Build the API prefix based on Strapi version
		String apiPrefix = "v4".equals(apiVersion) ? baseUrl + "/api" : baseUrl;

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + apiToken);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, apiPrefix, contentType, apiVersion, headers);
					case "delete" -> handleDelete(context, apiPrefix, contentType, headers);
					case "get" -> handleGet(context, apiPrefix, contentType, headers);
					case "getMany" -> handleGetMany(context, apiPrefix, contentType, apiVersion, headers);
					case "update" -> handleUpdate(context, apiPrefix, contentType, apiVersion, headers);
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

	private Map<String, Object> handleCreate(NodeExecutionContext context, String apiPrefix,
			String contentType, String apiVersion, Map<String, String> headers) throws Exception {
		String data = context.getParameter("data", "{}");
		Map<String, Object> entryData = parseJson(data);

		Map<String, Object> body;
		if ("v4".equals(apiVersion)) {
			body = Map.of("data", entryData);
		} else {
			body = entryData;
		}

		String url = apiPrefix + "/" + encode(contentType);
		HttpResponse<String> response = post(url, body, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, String apiPrefix,
			String contentType, Map<String, String> headers) throws Exception {
		String entryId = context.getParameter("entryId", "");
		String url = apiPrefix + "/" + encode(contentType) + "/" + encode(entryId);
		HttpResponse<String> response = delete(url, headers);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
		result.put("entryId", entryId);
		return result;
	}

	private Map<String, Object> handleGet(NodeExecutionContext context, String apiPrefix,
			String contentType, Map<String, String> headers) throws Exception {
		String entryId = context.getParameter("entryId", "");
		String url = apiPrefix + "/" + encode(contentType) + "/" + encode(entryId);
		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}

	private Map<String, Object> handleGetMany(NodeExecutionContext context, String apiPrefix,
			String contentType, String apiVersion, Map<String, String> headers) throws Exception {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 50);
		String sortFields = context.getParameter("sort", "");
		String publicationState = context.getParameter("publicationState", "");
		String where = context.getParameter("where", "");

		if (returnAll) {
			List<Map<String, Object>> allEntries = new ArrayList<>();
			int pageSize = 20;
			int page = 1;
			int start = 0;
			boolean hasMore = true;

			while (hasMore) {
				String url = apiPrefix + "/" + encode(contentType) + "?";
				if ("v4".equals(apiVersion)) {
					url += "pagination[pageSize]=" + pageSize + "&pagination[page]=" + page;
				} else {
					url += "_limit=" + pageSize + "&_start=" + start;
				}
				url = appendQueryOptions(url, sortFields, publicationState, where, apiVersion);

				HttpResponse<String> response = get(url, headers);
				List<Map<String, Object>> entries;
				if ("v4".equals(apiVersion)) {
					Map<String, Object> parsed = parseResponse(response);
					@SuppressWarnings("unchecked")
					List<Map<String, Object>> data = (List<Map<String, Object>>) parsed.get("data");
					entries = data != null ? data : List.of();
				} else {
					entries = parseArrayResponse(response);
				}
				if (!entries.isEmpty()) {
					allEntries.addAll(entries);
					page++;
					start += pageSize;
				} else {
					hasMore = false;
				}
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("entries", allEntries);
			return result;
		} else {
			String url = apiPrefix + "/" + encode(contentType) + "?";
			if ("v4".equals(apiVersion)) {
				url += "pagination[pageSize]=" + limit + "&pagination[page]=1";
			} else {
				url += "_limit=" + limit;
			}
			url = appendQueryOptions(url, sortFields, publicationState, where, apiVersion);

			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
	}

	private Map<String, Object> handleUpdate(NodeExecutionContext context, String apiPrefix,
			String contentType, String apiVersion, Map<String, String> headers) throws Exception {
		String entryId = context.getParameter("entryId", "");
		String data = context.getParameter("data", "{}");
		Map<String, Object> entryData = parseJson(data);

		Map<String, Object> body;
		if ("v4".equals(apiVersion)) {
			body = Map.of("data", entryData);
		} else {
			body = entryData;
		}

		String url = apiPrefix + "/" + encode(contentType) + "/" + encode(entryId);
		HttpResponse<String> response = put(url, body, headers);
		return parseResponse(response);
	}

	private String appendQueryOptions(String url, String sortFields, String publicationState,
			String where, String apiVersion) {
		if (!sortFields.isBlank()) {
			if ("v4".equals(apiVersion)) {
				url += "&sort=" + encode(sortFields);
			} else {
				url += "&_sort=" + encode(sortFields);
			}
		}
		if (!publicationState.isBlank()) {
			if ("v4".equals(apiVersion)) {
				url += "&publicationState=" + encode(publicationState);
			} else {
				url += "&_publicationState=" + encode(publicationState);
			}
		}
		if (!where.isBlank()) {
			url += "&" + where;
		}
		return url;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("apiVersion").displayName("API Version")
						.type(ParameterType.OPTIONS)
						.defaultValue("v4")
						.options(List.of(
								ParameterOption.builder().name("Strapi v4+").value("v4").build(),
								ParameterOption.builder().name("Strapi v3").value("v3").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("getMany")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("contentType").displayName("Content Type")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the content type to operate on.").build(),
				NodeParameter.builder()
						.name("entryId").displayName("Entry ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the entry.").build(),
				NodeParameter.builder()
						.name("data").displayName("Data (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Entry data as JSON for create/update.").build(),
				NodeParameter.builder()
						.name("publicationState").displayName("Publication State")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("All").value("").build(),
								ParameterOption.builder().name("Live").value("live").build(),
								ParameterOption.builder().name("Preview").value("preview").build()
						)).build(),
				NodeParameter.builder()
						.name("sort").displayName("Sort")
						.type(ParameterType.STRING).defaultValue("")
						.description("Sort fields (e.g. name:asc).").build(),
				NodeParameter.builder()
						.name("where").displayName("Where (JSON)")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON query to filter data.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build()
		);
	}
}
