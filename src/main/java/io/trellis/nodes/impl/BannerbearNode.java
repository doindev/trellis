package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Bannerbear — auto-generate images using the Bannerbear API.
 */
@Node(
		type = "bannerbear",
		displayName = "Bannerbear",
		description = "Auto-generate images with Bannerbear",
		category = "Miscellaneous",
		icon = "bannerbear",
		credentials = {"bannerbearApi"},
		searchOnly = true
)
public class BannerbearNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.bannerbear.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "image");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "image" -> handleImage(context, headers, operation);
					case "template" -> handleTemplate(context, headers, operation);
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

	private Map<String, Object> handleImage(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("template", context.getParameter("templateId", ""));
				String modificationsJson = context.getParameter("modifications", "[]");
				body.put("modifications", parseJson(modificationsJson));
				String webhookUrl = context.getParameter("webhookUrl", "");
				if (!webhookUrl.isEmpty()) body.put("webhook_url", webhookUrl);
				boolean transparent = toBoolean(context.getParameters().get("transparent"), false);
				if (transparent) body.put("transparent", true);
				HttpResponse<String> response = post(BASE_URL + "/images", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String imageId = context.getParameter("imageId", "");
				HttpResponse<String> response = get(BASE_URL + "/images/" + encode(imageId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown image operation: " + operation);
		};
	}

	private Map<String, Object> handleTemplate(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String templateId = context.getParameter("templateId", "");
				HttpResponse<String> response = get(BASE_URL + "/templates/" + encode(templateId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/templates", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown template operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("image")
						.options(List.of(
								ParameterOption.builder().name("Image").value("image").build(),
								ParameterOption.builder().name("Template").value("template").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("templateId").displayName("Template ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("imageId").displayName("Image ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("modifications").displayName("Modifications")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of template layer modifications.").build(),
				NodeParameter.builder()
						.name("webhookUrl").displayName("Webhook URL")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("transparent").displayName("Transparent Background")
						.type(ParameterType.BOOLEAN).defaultValue(false).build()
		);
	}
}
