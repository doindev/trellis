package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * FileMaker — manage records and run scripts via the FileMaker Data API.
 */
@Slf4j
@Node(
		type = "fileMaker",
		displayName = "FileMaker",
		description = "Manage FileMaker records and run scripts",
		category = "Miscellaneous",
		icon = "fileMaker",
		credentials = {"fileMakerApi"}
)
public class FileMakerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).required(true).defaultValue("record")
						.options(List.of(
								ParameterOption.builder().name("Record").value("record").build(),
								ParameterOption.builder().name("Script").value("script").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Duplicate Record").value("duplicateRecord").build(),
								ParameterOption.builder().name("Run").value("run").build()
						)).build(),
				NodeParameter.builder()
						.name("layout").displayName("Layout")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The layout to use for the operation.").build(),
				NodeParameter.builder()
						.name("recordId").displayName("Record ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the record.").build(),
				NodeParameter.builder()
						.name("fieldData").displayName("Field Data (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("The field data as JSON.").build(),
				NodeParameter.builder()
						.name("scriptName").displayName("Script Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the script to run.").build(),
				NodeParameter.builder()
						.name("scriptParameter").displayName("Script Parameter")
						.type(ParameterType.STRING).defaultValue("")
						.description("Parameter to pass to the script.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max number of results to return.").build(),
				NodeParameter.builder()
						.name("offset").displayName("Offset")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("The offset for pagination.").build(),
				NodeParameter.builder()
						.name("sortField").displayName("Sort Field")
						.type(ParameterType.STRING).defaultValue("")
						.description("Field to sort results by.").build(),
				NodeParameter.builder()
						.name("sortOrder").displayName("Sort Order")
						.type(ParameterType.OPTIONS).defaultValue("ascend")
						.options(List.of(
								ParameterOption.builder().name("Ascending").value("ascend").build(),
								ParameterOption.builder().name("Descending").value("descend").build()
						)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "record");
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		String host = String.valueOf(credentials.getOrDefault("host", ""));
		if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
		String database = String.valueOf(credentials.getOrDefault("database", ""));
		String baseUrl = host + "/fmi/data/v1/databases/" + encode(database);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// Authenticate and get token
				String token = authenticate(host, credentials);
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Content-Type", "application/json");
				headers.put("Authorization", "Bearer " + token);

				Map<String, Object> result = switch (resource) {
					case "record" -> handleRecord(context, baseUrl, headers, operation);
					case "script" -> handleScript(context, baseUrl, headers);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));

				// Logout to release the token
				logout(baseUrl, token);
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private String authenticate(String host, Map<String, Object> credentials) throws Exception {
		String username = String.valueOf(credentials.getOrDefault("username", ""));
		String password = String.valueOf(credentials.getOrDefault("password", ""));
		String database = String.valueOf(credentials.getOrDefault("database", ""));

		String authUrl = host + "/fmi/data/v1/databases/" + encode(database) + "/sessions";
		String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Basic " + auth);

		HttpResponse<String> response = post(authUrl, Map.of(), headers);
		Map<String, Object> parsed = parseResponse(response);

		@SuppressWarnings("unchecked")
		Map<String, Object> responseData = (Map<String, Object>) parsed.getOrDefault("response", Map.of());
		return String.valueOf(responseData.getOrDefault("token", ""));
	}

	private void logout(String baseUrl, String token) {
		try {
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Authorization", "Bearer " + token);
			delete(baseUrl + "/sessions/" + encode(token), headers);
		} catch (Exception e) {
			log.warn("Failed to logout FileMaker session: {}", e.getMessage());
		}
	}

	private Map<String, Object> handleRecord(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String layout = context.getParameter("layout", "");

		return switch (operation) {
			case "create" -> {
				String fieldData = context.getParameter("fieldData", "{}");
				Map<String, Object> body = Map.of("fieldData", parseJson(fieldData));
				HttpResponse<String> response = post(baseUrl + "/layouts/" + encode(layout) + "/records", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String recordId = context.getParameter("recordId", "");
				HttpResponse<String> response = delete(baseUrl + "/layouts/" + encode(layout) + "/records/" + encode(recordId), headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
				result.put("recordId", recordId);
				yield result;
			}
			case "get" -> {
				String recordId = context.getParameter("recordId", "");
				HttpResponse<String> response = get(baseUrl + "/layouts/" + encode(layout) + "/records/" + encode(recordId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);
				int limit = toInt(context.getParameter("limit", 50), 50);
				int offset = toInt(context.getParameter("offset", 1), 1);
				String sortField = context.getParameter("sortField", "");
				String sortOrder = context.getParameter("sortOrder", "ascend");

				String url = baseUrl + "/layouts/" + encode(layout) + "/records";
				url += "?_offset=" + offset + "&_limit=" + (returnAll ? 1000 : limit);
				if (!sortField.isEmpty()) {
					url += "&_sort=" + encode("[{\"fieldName\":\"" + sortField + "\",\"sortOrder\":\"" + sortOrder + "\"}]");
				}

				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String recordId = context.getParameter("recordId", "");
				String fieldData = context.getParameter("fieldData", "{}");
				Map<String, Object> body = Map.of("fieldData", parseJson(fieldData));
				HttpResponse<String> response = patch(baseUrl + "/layouts/" + encode(layout) + "/records/" + encode(recordId), body, headers);
				yield parseResponse(response);
			}
			case "duplicateRecord" -> {
				String recordId = context.getParameter("recordId", "");
				HttpResponse<String> response = post(baseUrl + "/layouts/" + encode(layout) + "/records/" + encode(recordId), Map.of(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown record operation: " + operation);
		};
	}

	private Map<String, Object> handleScript(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers) throws Exception {
		String layout = context.getParameter("layout", "");
		String scriptName = context.getParameter("scriptName", "");
		String scriptParam = context.getParameter("scriptParameter", "");

		String url = baseUrl + "/layouts/" + encode(layout) + "/script/" + encode(scriptName);
		if (!scriptParam.isEmpty()) {
			url += "?script.param=" + encode(scriptParam);
		}

		HttpResponse<String> response = get(url, headers);
		return parseResponse(response);
	}
}
