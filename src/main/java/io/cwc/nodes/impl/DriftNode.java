package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Drift — manage contacts in the Drift conversational marketing platform.
 */
@Node(
		type = "drift",
		displayName = "Drift",
		description = "Manage contacts in Drift",
		category = "Customer Support",
		icon = "drift",
		credentials = {"driftApi"}
)
public class DriftNode extends AbstractApiNode {

	private static final String BASE_URL = "https://driftapi.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");

		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						String email = context.getParameter("email", "");
						Map<String, Object> attributes = new LinkedHashMap<>();
						attributes.put("email", email);
						String name = context.getParameter("name", "");
						if (!name.isEmpty()) attributes.put("name", name);
						String phone = context.getParameter("phone", "");
						if (!phone.isEmpty()) attributes.put("phone", phone);
						Map<String, Object> body = Map.of("attributes", attributes);
						HttpResponse<String> response = post(BASE_URL + "/contacts", body, headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", parsed);
						yield data;
					}
					case "update" -> {
						String contactId = context.getParameter("contactId", "");
						Map<String, Object> attributes = new LinkedHashMap<>();
						String name = context.getParameter("name", "");
						if (!name.isEmpty()) attributes.put("name", name);
						String phone = context.getParameter("phone", "");
						if (!phone.isEmpty()) attributes.put("phone", phone);
						String email = context.getParameter("email", "");
						if (!email.isEmpty()) attributes.put("email", email);
						Map<String, Object> body = Map.of("attributes", attributes);
						HttpResponse<String> response = patch(BASE_URL + "/contacts/" + contactId, body, headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", parsed);
						yield data;
					}
					case "get" -> {
						String contactId = context.getParameter("contactId", "");
						HttpResponse<String> response = get(BASE_URL + "/contacts/" + contactId, headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", parsed);
						yield data;
					}
					case "getCustomAttributes" -> {
						HttpResponse<String> response = get(BASE_URL + "/contacts/attributes", headers);
						Map<String, Object> parsed = parseResponse(response);
						@SuppressWarnings("unchecked")
						Map<String, Object> data = (Map<String, Object>) parsed.getOrDefault("data", parsed);
						yield data;
					}
					case "delete" -> {
						String contactId = context.getParameter("contactId", "");
						delete(BASE_URL + "/contacts/" + contactId, headers);
						yield Map.<String, Object>of("success", true);
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
						.type(ParameterType.OPTIONS).defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Custom Attributes").value("getCustomAttributes").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("ID of the contact.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address of the contact.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
