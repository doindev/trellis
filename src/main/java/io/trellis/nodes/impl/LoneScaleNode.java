package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * LoneScale — manage lists and items using the LoneScale API.
 */
@Node(
		type = "loneScale",
		displayName = "LoneScale",
		description = "Manage lists and items with LoneScale",
		category = "Miscellaneous",
		icon = "loneScale",
		credentials = {"loneScaleApi"}
)
public class LoneScaleNode extends AbstractApiNode {

	private static final String BASE_URL = "https://public-api.lonescale.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "list");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-API-KEY", apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "list" -> handleList(context, headers, operation);
					case "item" -> handleItem(context, headers, operation);
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

	private Map<String, Object> handleList(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("name", ""));
				body.put("entity", context.getParameter("entity", "PEOPLE"));
				HttpResponse<String> response = post(BASE_URL + "/lists", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown list operation: " + operation);
		};
	}

	private Map<String, Object> handleItem(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String listId = context.getParameter("listId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String companyName = context.getParameter("companyName", "");
				if (!companyName.isEmpty()) body.put("company_name", companyName);
				String domain = context.getParameter("domain", "");
				if (!domain.isEmpty()) body.put("domain", domain);
				String linkedinUrl = context.getParameter("linkedinUrl", "");
				if (!linkedinUrl.isEmpty()) body.put("linkedin_url", linkedinUrl);
				String currentPosition = context.getParameter("currentPosition", "");
				if (!currentPosition.isEmpty()) body.put("current_position", currentPosition);
				String location = context.getParameter("location", "");
				if (!location.isEmpty()) body.put("location", location);
				HttpResponse<String> response = post(BASE_URL + "/lists/" + encode(listId) + "/item", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown item operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("list")
						.options(List.of(
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Item").value("item").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build()
						)).build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("List name.").build(),
				NodeParameter.builder()
						.name("entity").displayName("Entity Type")
						.type(ParameterType.OPTIONS).defaultValue("PEOPLE")
						.options(List.of(
								ParameterOption.builder().name("Company").value("COMPANY").build(),
								ParameterOption.builder().name("People").value("PEOPLE").build()
						)).build(),
				NodeParameter.builder()
						.name("listId").displayName("List ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("companyName").displayName("Company Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("linkedinUrl").displayName("LinkedIn URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("currentPosition").displayName("Current Position")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("location").displayName("Location")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
