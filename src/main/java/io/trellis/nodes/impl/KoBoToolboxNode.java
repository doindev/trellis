package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * KoBoToolbox — manage forms, submissions, and hooks using the KoBoToolbox API.
 */
@Node(
		type = "koBoToolbox",
		displayName = "KoBoToolbox",
		description = "Manage forms and submissions with KoBoToolbox",
		category = "Surveys & Forms",
		icon = "koBoToolbox",
		credentials = {"koBoToolboxApi"}
)
public class KoBoToolboxNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "https://kf.kobotoolbox.org");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String token = context.getCredentialString("token", "");

		String resource = context.getParameter("resource", "submission");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Token " + token);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "form" -> handleForm(context, baseUrl, headers, operation);
					case "submission" -> handleSubmission(context, baseUrl, headers, operation);
					case "hook" -> handleHook(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleForm(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String formId = context.getParameter("formId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "redeploy" -> {
				String formId = context.getParameter("formId", "");
				HttpResponse<String> response = patch(baseUrl + "/api/v2/assets/" + encode(formId) + "/deployment/", Map.of(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown form operation: " + operation);
		};
	}

	private Map<String, Object> handleSubmission(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String formId = context.getParameter("formId", "");
		return switch (operation) {
			case "delete" -> {
				String submissionId = context.getParameter("submissionId", "");
				HttpResponse<String> response = delete(baseUrl + "/api/v2/assets/" + encode(formId) + "/data/" + encode(submissionId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String submissionId = context.getParameter("submissionId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId) + "/data/" + encode(submissionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				StringBuilder url = new StringBuilder(baseUrl + "/api/v2/assets/" + encode(formId) + "/data/?limit=" + limit);
				String filterJson = context.getParameter("filterJson", "");
				if (!filterJson.isEmpty()) url.append("&query=").append(encode(filterJson));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "getValidation" -> {
				String submissionId = context.getParameter("submissionId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId) + "/data/" + encode(submissionId) + "/validation_status/", headers);
				yield parseResponse(response);
			}
			case "setValidation" -> {
				String submissionId = context.getParameter("submissionId", "");
				String validationStatus = context.getParameter("validationStatus", "");
				Map<String, Object> body = Map.of("validation_status.uid", validationStatus);
				HttpResponse<String> response = patch(baseUrl + "/api/v2/assets/" + encode(formId) + "/data/" + encode(submissionId) + "/validation_status/", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown submission operation: " + operation);
		};
	}

	private Map<String, Object> handleHook(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String formId = context.getParameter("formId", "");
		return switch (operation) {
			case "get" -> {
				String hookId = context.getParameter("hookId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId) + "/hooks/" + encode(hookId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId) + "/hooks/?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "retryAll" -> {
				String hookId = context.getParameter("hookId", "");
				HttpResponse<String> response = patch(baseUrl + "/api/v2/assets/" + encode(formId) + "/hooks/" + encode(hookId) + "/retry/", Map.of(), headers);
				yield parseResponse(response);
			}
			case "getLogs" -> {
				String hookId = context.getParameter("hookId", "");
				HttpResponse<String> response = get(baseUrl + "/api/v2/assets/" + encode(formId) + "/hooks/" + encode(hookId) + "/logs/", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown hook operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("submission")
						.options(List.of(
								ParameterOption.builder().name("Form").value("form").build(),
								ParameterOption.builder().name("Hook").value("hook").build(),
								ParameterOption.builder().name("Submission").value("submission").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Logs").value("getLogs").build(),
								ParameterOption.builder().name("Get Validation Status").value("getValidation").build(),
								ParameterOption.builder().name("Redeploy").value("redeploy").build(),
								ParameterOption.builder().name("Retry All").value("retryAll").build(),
								ParameterOption.builder().name("Set Validation Status").value("setValidation").build()
						)).build(),
				NodeParameter.builder()
						.name("formId").displayName("Form ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("The KoBoToolbox form/asset ID.").build(),
				NodeParameter.builder()
						.name("submissionId").displayName("Submission ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("hookId").displayName("Hook ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("validationStatus").displayName("Validation Status")
						.type(ParameterType.OPTIONS).defaultValue("validation_status_approved")
						.options(List.of(
								ParameterOption.builder().name("Approved").value("validation_status_approved").build(),
								ParameterOption.builder().name("Not Approved").value("validation_status_not_approved").build(),
								ParameterOption.builder().name("On Hold").value("validation_status_on_hold").build()
						)).build(),
				NodeParameter.builder()
						.name("filterJson").displayName("Filter (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("Filter submissions using JSON query.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
