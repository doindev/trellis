package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * APITemplate.io — generate images and PDFs using the APITemplate.io API.
 */
@Node(
		type = "apiTemplateIo",
		displayName = "APITemplate.io",
		description = "Generate images and PDFs with APITemplate.io",
		category = "Miscellaneous",
		icon = "apiTemplateIo",
		credentials = {"apiTemplateIoApi"}
)
public class ApiTemplateIoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.apitemplate.io/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "image");
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
					case "account" -> handleAccount(headers, operation);
					case "image" -> handleImage(context, headers, operation);
					case "pdf" -> handlePdf(context, headers, operation);
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

	private Map<String, Object> handleAccount(Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/account-information", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown account operation: " + operation);
		};
	}

	private Map<String, Object> handleImage(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String templateId = context.getParameter("templateId", "");
				String overridesJson = context.getParameter("overrides", "[]");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("overrides", parseJson(overridesJson));
				HttpResponse<String> response = post(BASE_URL + "/create?template_id=" + encode(templateId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown image operation: " + operation);
		};
	}

	private Map<String, Object> handlePdf(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				String templateId = context.getParameter("templateId", "");
				String propertiesJson = context.getParameter("properties", "{}");
				Map<String, Object> body = new LinkedHashMap<>();
				Object properties = parseJson(propertiesJson);
				if (properties instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> props = (Map<String, Object>) properties;
					body.putAll(props);
				}
				HttpResponse<String> response = post(BASE_URL + "/create?template_id=" + encode(templateId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown PDF operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("image")
						.options(List.of(
								ParameterOption.builder().name("Account").value("account").build(),
								ParameterOption.builder().name("Image").value("image").build(),
								ParameterOption.builder().name("PDF").value("pdf").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build()
						)).build(),
				NodeParameter.builder()
						.name("templateId").displayName("Template ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The template to use for generation.").build(),
				NodeParameter.builder()
						.name("overrides").displayName("Overrides")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of layer modifications for image generation.").build(),
				NodeParameter.builder()
						.name("properties").displayName("Properties")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object of template properties for PDF generation.").build()
		);
	}
}
