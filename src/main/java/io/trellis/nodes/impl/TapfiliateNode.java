package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Tapfiliate — manage affiliates and programs using the Tapfiliate API.
 */
@Node(
		type = "tapfiliate",
		displayName = "Tapfiliate",
		description = "Manage affiliates and programs in Tapfiliate",
		category = "Miscellaneous",
		icon = "tapfiliate",
		credentials = {"tapfiliateApi"}
)
public class TapfiliateNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.tapfiliate.com/1.6";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String resource = context.getParameter("resource", "affiliate");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Api-Key", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "affiliate" -> handleAffiliate(context, headers, operation);
					case "affiliateMetadata" -> handleAffiliateMetadata(context, headers, operation);
					case "programAffiliate" -> handleProgramAffiliate(context, headers, operation);
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

	private Map<String, Object> handleAffiliate(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("firstname", context.getParameter("firstName", ""));
				body.put("lastname", context.getParameter("lastName", ""));
				body.put("email", context.getParameter("email", ""));
				String company = context.getParameter("company", "");
				if (!company.isEmpty()) body.put("company", Map.of("name", company));
				HttpResponse<String> response = post(BASE_URL + "/affiliates/", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String affiliateId = context.getParameter("affiliateId", "");
				HttpResponse<String> response = delete(BASE_URL + "/affiliates/" + encode(affiliateId) + "/", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String affiliateId = context.getParameter("affiliateId", "");
				HttpResponse<String> response = get(BASE_URL + "/affiliates/" + encode(affiliateId) + "/", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/affiliates/?page_size=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown affiliate operation: " + operation);
		};
	}

	private Map<String, Object> handleAffiliateMetadata(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String affiliateId = context.getParameter("affiliateId", "");
		String key = context.getParameter("metadataKey", "");
		return switch (operation) {
			case "add", "update" -> {
				String value = context.getParameter("metadataValue", "");
				Map<String, Object> body = Map.of("key", key, "value", value);
				HttpResponse<String> response = put(BASE_URL + "/affiliates/" + encode(affiliateId) + "/meta-data/", body, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				HttpResponse<String> response = delete(BASE_URL + "/affiliates/" + encode(affiliateId) + "/meta-data/" + encode(key) + "/", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown metadata operation: " + operation);
		};
	}

	private Map<String, Object> handleProgramAffiliate(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String programId = context.getParameter("programId", "");
		String affiliateId = context.getParameter("affiliateId", "");
		return switch (operation) {
			case "add" -> {
				Map<String, Object> body = Map.of("affiliate", Map.of("id", affiliateId));
				HttpResponse<String> response = post(BASE_URL + "/programs/" + encode(programId) + "/affiliates/", body, headers);
				yield parseResponse(response);
			}
			case "approve" -> {
				HttpResponse<String> response = put(BASE_URL + "/programs/" + encode(programId) + "/affiliates/" + encode(affiliateId) + "/approved/", Map.of(), headers);
				yield parseResponse(response);
			}
			case "disapprove" -> {
				HttpResponse<String> response = delete(BASE_URL + "/programs/" + encode(programId) + "/affiliates/" + encode(affiliateId) + "/approved/", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/programs/" + encode(programId) + "/affiliates/" + encode(affiliateId) + "/", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(BASE_URL + "/programs/" + encode(programId) + "/affiliates/?page_size=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown program affiliate operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("affiliate")
						.options(List.of(
								ParameterOption.builder().name("Affiliate").value("affiliate").build(),
								ParameterOption.builder().name("Affiliate Metadata").value("affiliateMetadata").build(),
								ParameterOption.builder().name("Program Affiliate").value("programAffiliate").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Approve").value("approve").build(),
								ParameterOption.builder().name("Disapprove").value("disapprove").build()
						)).build(),
				NodeParameter.builder()
						.name("affiliateId").displayName("Affiliate ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("company").displayName("Company")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("programId").displayName("Program ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("metadataKey").displayName("Metadata Key")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("metadataValue").displayName("Metadata Value")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
