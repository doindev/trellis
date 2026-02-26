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
 * ERPNext — manage documents via the ERPNext REST API.
 */
@Slf4j
@Node(
		type = "erpNext",
		displayName = "ERPNext",
		description = "Manage ERPNext documents",
		category = "Miscellaneous",
		icon = "erpNext",
		credentials = {"erpNextApi"}
)
public class ErpNextNode extends AbstractApiNode {

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
						.type(ParameterType.OPTIONS).required(true).defaultValue("document")
						.options(List.of(
								ParameterOption.builder().name("Document").value("document").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).required(true).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("docType").displayName("DocType")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The document type (e.g. Customer, Sales Order, Item).").build(),
				NodeParameter.builder()
						.name("documentName").displayName("Document Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name/ID of the document.").build(),
				NodeParameter.builder()
						.name("properties").displayName("Properties (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Document properties as JSON.").build(),
				NodeParameter.builder()
						.name("filters").displayName("Filters (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("Filters as JSON array (e.g. [[\"status\",\"=\",\"Open\"]]).").build(),
				NodeParameter.builder()
						.name("fields").displayName("Fields")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of fields to return.").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(20)
						.description("Max number of results to return.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "get");
		Map<String, Object> credentials = context.getCredentials();

		String url = String.valueOf(credentials.getOrDefault("url", ""));
		if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
		String baseUrl = url + "/api";

		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		String apiSecret = String.valueOf(credentials.getOrDefault("apiSecret", ""));

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "token " + apiKey + ":" + apiSecret);

		String docType = context.getParameter("docType", "");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						String properties = context.getParameter("properties", "{}");
						Map<String, Object> body = parseJson(properties);
						HttpResponse<String> response = post(baseUrl + "/resource/" + encode(docType), body, headers);
						yield parseResponse(response);
					}
					case "delete" -> {
						String name = context.getParameter("documentName", "");
						HttpResponse<String> response = delete(baseUrl + "/resource/" + encode(docType) + "/" + encode(name), headers);
						Map<String, Object> res = new LinkedHashMap<>();
						res.put("success", response.statusCode() >= 200 && response.statusCode() < 300);
						res.put("docType", docType);
						res.put("name", name);
						yield res;
					}
					case "get" -> {
						String name = context.getParameter("documentName", "");
						String fields = context.getParameter("fields", "");
						String getUrl = baseUrl + "/resource/" + encode(docType) + "/" + encode(name);
						if (!fields.isEmpty()) {
							getUrl += "?fields=" + encode("[\"" + String.join("\",\"", fields.split(",")) + "\"]");
						}
						HttpResponse<String> response = get(getUrl, headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						boolean returnAll = toBoolean(context.getParameter("returnAll", false), false);
						int limit = toInt(context.getParameter("limit", 20), 20);
						String filters = context.getParameter("filters", "[]");
						String fields = context.getParameter("fields", "");

						String listUrl = baseUrl + "/resource/" + encode(docType);
						listUrl += "?limit_page_length=" + (returnAll ? 0 : limit);
						if (!"[]".equals(filters) && !filters.isEmpty()) {
							listUrl += "&filters=" + encode(filters);
						}
						if (!fields.isEmpty()) {
							listUrl += "&fields=" + encode("[\"" + String.join("\",\"", fields.split(",")) + "\"]");
						}

						HttpResponse<String> response = get(listUrl, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String name = context.getParameter("documentName", "");
						String properties = context.getParameter("properties", "{}");
						Map<String, Object> body = parseJson(properties);
						HttpResponse<String> response = put(baseUrl + "/resource/" + encode(docType) + "/" + encode(name), body, headers);
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
}
