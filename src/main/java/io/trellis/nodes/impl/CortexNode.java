package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Cortex — execute analyzers and responders using the Cortex API.
 */
@Node(
		type = "cortex",
		displayName = "Cortex",
		description = "Execute analyzers and responders with Cortex",
		category = "Miscellaneous",
		icon = "cortex",
		credentials = {"cortexApi"}
)
public class CortexNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String host = context.getCredentialString("host", "");
		if (host.endsWith("/")) host = host.substring(0, host.length() - 1);
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "analyzer");
		String operation = context.getParameter("operation", "execute");

		String baseUrl = host + "/api";

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "analyzer" -> handleAnalyzer(context, baseUrl, headers, operation);
					case "job" -> handleJob(context, baseUrl, headers, operation);
					case "responder" -> handleResponder(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleAnalyzer(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "execute" -> {
				String analyzerId = context.getParameter("analyzerId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("dataType", context.getParameter("dataType", ""));
				body.put("data", context.getParameter("data", ""));
				int tlp = toInt(context.getParameters().get("tlp"), 2);
				body.put("tlp", tlp);
				HttpResponse<String> response = post(baseUrl + "/analyzer/" + encode(analyzerId) + "/run", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown analyzer operation: " + operation);
		};
	}

	private Map<String, Object> handleJob(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String jobId = context.getParameter("jobId", "");
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(baseUrl + "/job/" + encode(jobId), headers);
				yield parseResponse(response);
			}
			case "report" -> {
				HttpResponse<String> response = get(baseUrl + "/job/" + encode(jobId) + "/report", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown job operation: " + operation);
		};
	}

	private Map<String, Object> handleResponder(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "execute" -> {
				String responderId = context.getParameter("responderId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("responderId", responderId);
				body.put("dataType", context.getParameter("dataType", ""));
				body.put("data", context.getParameter("data", ""));
				int tlp = toInt(context.getParameters().get("tlp"), 2);
				body.put("tlp", tlp);
				String message = context.getParameter("message", "");
				if (!message.isEmpty()) body.put("message", message);
				HttpResponse<String> response = post(baseUrl + "/responder/" + encode(responderId) + "/run", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown responder operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("analyzer")
						.options(List.of(
								ParameterOption.builder().name("Analyzer").value("analyzer").build(),
								ParameterOption.builder().name("Job").value("job").build(),
								ParameterOption.builder().name("Responder").value("responder").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("execute")
						.options(List.of(
								ParameterOption.builder().name("Execute").value("execute").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Report").value("report").build()
						)).build(),
				NodeParameter.builder()
						.name("analyzerId").displayName("Analyzer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("responderId").displayName("Responder ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("jobId").displayName("Job ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dataType").displayName("Data Type")
						.type(ParameterType.OPTIONS).defaultValue("ip")
						.options(List.of(
								ParameterOption.builder().name("Domain").value("domain").build(),
								ParameterOption.builder().name("File").value("file").build(),
								ParameterOption.builder().name("Filename").value("filename").build(),
								ParameterOption.builder().name("Fqdn").value("fqdn").build(),
								ParameterOption.builder().name("Hash").value("hash").build(),
								ParameterOption.builder().name("IP").value("ip").build(),
								ParameterOption.builder().name("Mail").value("mail").build(),
								ParameterOption.builder().name("Mail Subject").value("mail_subject").build(),
								ParameterOption.builder().name("Other").value("other").build(),
								ParameterOption.builder().name("Registry").value("registry").build(),
								ParameterOption.builder().name("URI Path").value("uri_path").build(),
								ParameterOption.builder().name("URL").value("url").build(),
								ParameterOption.builder().name("User Agent").value("user-agent").build()
						)).build(),
				NodeParameter.builder()
						.name("data").displayName("Data")
						.type(ParameterType.STRING).defaultValue("")
						.description("Observable data to analyze.").build(),
				NodeParameter.builder()
						.name("tlp").displayName("TLP")
						.type(ParameterType.OPTIONS).defaultValue("2")
						.options(List.of(
								ParameterOption.builder().name("White").value("0").build(),
								ParameterOption.builder().name("Green").value("1").build(),
								ParameterOption.builder().name("Amber").value("2").build(),
								ParameterOption.builder().name("Red").value("3").build()
						))
						.description("Traffic Light Protocol level.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("Message for responder execution.").build()
		);
	}
}
