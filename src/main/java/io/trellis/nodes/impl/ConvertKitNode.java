package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * ConvertKit — manage subscribers, forms, sequences, and tags using the ConvertKit API.
 */
@Node(
		type = "convertKit",
		displayName = "ConvertKit",
		description = "Manage subscribers and campaigns in ConvertKit",
		category = "Marketing",
		icon = "convertKit",
		credentials = {"convertKitApi"}
)
public class ConvertKitNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.convertkit.com/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String apiSecret = (String) credentials.getOrDefault("apiSecret", "");

		String resource = context.getParameter("resource", "tag");
		String operation = context.getParameter("operation", "getAll");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "customField" -> handleCustomField(context, apiKey, apiSecret, operation);
					case "form" -> handleForm(context, apiKey, apiSecret, operation);
					case "sequence" -> handleSequence(context, apiKey, apiSecret, operation);
					case "tag" -> handleTag(context, apiKey, apiSecret, operation);
					case "tagSubscriber" -> handleTagSubscriber(context, apiKey, apiSecret, operation);
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

	private Map<String, String> jsonHeaders() {
		return Map.of("Content-Type", "application/json", "Accept", "application/json");
	}

	private Map<String, Object> handleCustomField(NodeExecutionContext context, String apiKey, String apiSecret, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = Map.of("api_secret", apiSecret, "label", context.getParameter("label", ""));
				HttpResponse<String> response = post(BASE_URL + "/custom_fields", body, jsonHeaders());
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/custom_fields?api_key=" + encode(apiKey), jsonHeaders());
				yield parseResponse(response);
			}
			case "delete" -> {
				String fieldId = context.getParameter("fieldId", "");
				HttpResponse<String> response = delete(BASE_URL + "/custom_fields/" + encode(fieldId) + "?api_secret=" + encode(apiSecret), jsonHeaders());
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown custom field operation: " + operation);
		};
	}

	private Map<String, Object> handleForm(NodeExecutionContext context, String apiKey, String apiSecret, String operation) throws Exception {
		return switch (operation) {
			case "addSubscriber" -> {
				String formId = context.getParameter("formId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_secret", apiSecret);
				body.put("email", context.getParameter("email", ""));
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				HttpResponse<String> response = post(BASE_URL + "/forms/" + encode(formId) + "/subscribe", body, jsonHeaders());
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/forms?api_key=" + encode(apiKey), jsonHeaders());
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown form operation: " + operation);
		};
	}

	private Map<String, Object> handleSequence(NodeExecutionContext context, String apiKey, String apiSecret, String operation) throws Exception {
		return switch (operation) {
			case "addSubscriber" -> {
				String sequenceId = context.getParameter("sequenceId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_secret", apiSecret);
				body.put("email", context.getParameter("email", ""));
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				HttpResponse<String> response = post(BASE_URL + "/sequences/" + encode(sequenceId) + "/subscribe", body, jsonHeaders());
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/sequences?api_key=" + encode(apiKey), jsonHeaders());
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown sequence operation: " + operation);
		};
	}

	private Map<String, Object> handleTag(NodeExecutionContext context, String apiKey, String apiSecret, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String name = context.getParameter("tagName", "");
				Map<String, Object> body = Map.of("api_secret", apiSecret, "tag", Map.of("name", name));
				HttpResponse<String> response = post(BASE_URL + "/tags", body, jsonHeaders());
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/tags?api_key=" + encode(apiKey), jsonHeaders());
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown tag operation: " + operation);
		};
	}

	private Map<String, Object> handleTagSubscriber(NodeExecutionContext context, String apiKey, String apiSecret, String operation) throws Exception {
		String tagId = context.getParameter("tagId", "");
		return switch (operation) {
			case "add" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("api_secret", apiSecret);
				body.put("email", context.getParameter("email", ""));
				HttpResponse<String> response = post(BASE_URL + "/tags/" + encode(tagId) + "/subscribe", body, jsonHeaders());
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/tags/" + encode(tagId) + "/subscriptions?api_key=" + encode(apiKey), jsonHeaders());
				yield parseResponse(response);
			}
			case "delete" -> {
				Map<String, Object> body = Map.of("api_secret", apiSecret, "email", context.getParameter("email", ""));
				HttpResponse<String> response = post(BASE_URL + "/tags/" + encode(tagId) + "/unsubscribe", body, jsonHeaders());
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown tag subscriber operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("tag")
						.options(List.of(
								ParameterOption.builder().name("Custom Field").value("customField").build(),
								ParameterOption.builder().name("Form").value("form").build(),
								ParameterOption.builder().name("Sequence").value("sequence").build(),
								ParameterOption.builder().name("Tag").value("tag").build(),
								ParameterOption.builder().name("Tag Subscriber").value("tagSubscriber").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add Subscriber").value("addSubscriber").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Add").value("add").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sequenceId").displayName("Sequence ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tagId").displayName("Tag ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tagName").displayName("Tag Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("label").displayName("Label")
						.type(ParameterType.STRING).defaultValue("")
						.description("Label for custom field.").build(),
				NodeParameter.builder()
						.name("fieldId").displayName("Field ID")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
