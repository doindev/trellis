package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * E-goi — manage contacts for email marketing via the E-goi API.
 */
@Node(
		type = "egoi",
		displayName = "E-goi",
		description = "Manage contacts in E-goi",
		category = "Marketing",
		icon = "egoi",
		credentials = {"egoiApi"},
		searchOnly = true
)
public class EgoiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.egoiapp.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "create");
		String listId = context.getParameter("listId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Apikey", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> {
						Map<String, Object> body = new LinkedHashMap<>();
						Map<String, Object> baseFields = new LinkedHashMap<>();
						baseFields.put("email", context.getParameter("email", ""));
						String firstName = context.getParameter("firstName", "");
						if (!firstName.isEmpty()) baseFields.put("first_name", firstName);
						String lastName = context.getParameter("lastName", "");
						if (!lastName.isEmpty()) baseFields.put("last_name", lastName);
						String cellphone = context.getParameter("cellphone", "");
						if (!cellphone.isEmpty()) baseFields.put("cellphone", cellphone);
						body.put("base", baseFields);
						HttpResponse<String> response = post(BASE_URL + "/lists/" + encode(listId) + "/contacts", body, headers);
						yield parseResponse(response);
					}
					case "get" -> {
						String contactId = context.getParameter("contactId", "");
						HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(listId) + "/contacts/" + encode(contactId), headers);
						yield parseResponse(response);
					}
					case "getAll" -> {
						int limit = toInt(context.getParameters().get("limit"), 100);
						HttpResponse<String> response = get(BASE_URL + "/lists/" + encode(listId) + "/contacts?limit=" + limit, headers);
						yield parseResponse(response);
					}
					case "update" -> {
						String contactId = context.getParameter("contactId", "");
						Map<String, Object> body = new LinkedHashMap<>();
						Map<String, Object> baseFields = new LinkedHashMap<>();
						String firstName = context.getParameter("firstName", "");
						if (!firstName.isEmpty()) baseFields.put("first_name", firstName);
						String lastName = context.getParameter("lastName", "");
						if (!lastName.isEmpty()) baseFields.put("last_name", lastName);
						String cellphone = context.getParameter("cellphone", "");
						if (!cellphone.isEmpty()) baseFields.put("cellphone", cellphone);
						if (!baseFields.isEmpty()) body.put("base", baseFields);
						HttpResponse<String> response = patch(BASE_URL + "/lists/" + encode(listId) + "/contacts/" + encode(contactId), body, headers);
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
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The E-goi list ID.").required(true).build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
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
						.name("cellphone").displayName("Cellphone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max contacts to return (1-500).").build()
		);
	}
}
