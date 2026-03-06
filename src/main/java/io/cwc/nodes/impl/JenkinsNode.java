package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Jenkins — manage jobs, builds, and instance operations in Jenkins CI/CD.
 */
@Node(
		type = "jenkins",
		displayName = "Jenkins",
		description = "Manage jobs, builds, and instance operations in Jenkins",
		category = "Development / DevOps",
		icon = "jenkins",
		credentials = {"jenkinsApi"}
)
public class JenkinsNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String baseUrl = (String) credentials.getOrDefault("baseUrl", "");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String username = (String) credentials.getOrDefault("username", "");
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String resource = context.getParameter("resource", "job");
		String operation = context.getParameter("operation", "trigger");

		Map<String, String> headers = new HashMap<>();
		String auth = Base64.getEncoder().encodeToString((username + ":" + apiKey).getBytes());
		headers.put("Authorization", "Basic " + auth);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "job" -> handleJob(context, baseUrl, headers, operation);
					case "build" -> handleBuild(context, baseUrl, headers, operation);
					case "instance" -> handleInstance(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleJob(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "trigger" -> {
				String jobName = context.getParameter("jobName", "");
				String url = baseUrl + "/job/" + encode(jobName) + "/build";
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield Map.of("success", true, "statusCode", response.statusCode());
			}
			case "triggerWithParams" -> {
				String jobName = context.getParameter("jobName", "");
				String url = baseUrl + "/job/" + encode(jobName) + "/buildWithParameters";
				// Parameters would be passed as query params or form data
				String paramsJson = context.getParameter("buildParameters", "");
				Map<String, Object> body = paramsJson.isEmpty() ? Map.of() : parseJson(paramsJson);
				HttpResponse<String> response = post(url, body, headers);
				yield Map.of("success", true, "statusCode", response.statusCode());
			}
			case "copy" -> {
				String existingJob = context.getParameter("existingJob", "");
				String newJob = context.getParameter("newJob", "");
				String url = baseUrl + "/createItem?name=" + encode(newJob) + "&mode=copy&from=" + encode(existingJob);
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield Map.of("success", true, "statusCode", response.statusCode());
			}
			case "create" -> {
				String newJob = context.getParameter("newJob", "");
				String xml = context.getParameter("xml", "");
				String url = baseUrl + "/createItem?name=" + encode(newJob);
				Map<String, String> xmlHeaders = new HashMap<>(headers);
				xmlHeaders.put("Content-Type", "application/xml");
				HttpResponse<String> response = postRaw(url, xmlHeaders, xml);
				yield Map.of("success", true, "statusCode", response.statusCode());
			}
			default -> throw new IllegalArgumentException("Unknown job operation: " + operation);
		};
	}

	private Map<String, Object> handleBuild(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		if ("getAll".equals(operation)) {
			String jobName = context.getParameter("jobName", "");
			int limit = toInt(context.getParameters().get("limit"), 50);
			String url = baseUrl + "/job/" + encode(jobName) + "/api/json?tree=builds[*]{0," + limit + "}";
			HttpResponse<String> response = get(url, headers);
			return parseResponse(response);
		}
		throw new IllegalArgumentException("Unknown build operation: " + operation);
	}

	private Map<String, Object> handleInstance(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String endpoint = switch (operation) {
			case "quietDown" -> "/quietDown";
			case "cancelQuietDown" -> "/cancelQuietDown";
			case "restart" -> "/restart";
			case "safeRestart" -> "/safeRestart";
			case "exit" -> "/exit";
			case "safeExit" -> "/safeExit";
			default -> throw new IllegalArgumentException("Unknown instance operation: " + operation);
		};

		String url = baseUrl + endpoint;
		if ("quietDown".equals(operation)) {
			String reason = context.getParameter("reason", "");
			if (!reason.isEmpty()) url += "?reason=" + encode(reason);
		}

		HttpResponse<String> response = post(url, Map.of(), headers);
		return Map.of("success", true, "operation", operation, "statusCode", response.statusCode());
	}

	private HttpResponse<String> postRaw(String url, Map<String, String> headers, String body) throws Exception {
		java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
		java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
				.uri(java.net.URI.create(url))
				.POST(java.net.http.HttpRequest.BodyPublishers.ofString(body));
		headers.forEach(builder::header);
		return client.send(builder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("job")
						.options(List.of(
								ParameterOption.builder().name("Build").value("build").build(),
								ParameterOption.builder().name("Instance").value("instance").build(),
								ParameterOption.builder().name("Job").value("job").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("trigger")
						.options(List.of(
								ParameterOption.builder().name("Trigger").value("trigger").build(),
								ParameterOption.builder().name("Trigger With Parameters").value("triggerWithParams").build(),
								ParameterOption.builder().name("Copy").value("copy").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Quiet Down").value("quietDown").build(),
								ParameterOption.builder().name("Cancel Quiet Down").value("cancelQuietDown").build(),
								ParameterOption.builder().name("Restart").value("restart").build(),
								ParameterOption.builder().name("Safe Restart").value("safeRestart").build(),
								ParameterOption.builder().name("Exit").value("exit").build(),
								ParameterOption.builder().name("Safe Exit").value("safeExit").build()
						)).build(),
				NodeParameter.builder()
						.name("jobName").displayName("Job Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the Jenkins job.").build(),
				NodeParameter.builder()
						.name("existingJob").displayName("Existing Job")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name of the job to copy from.").build(),
				NodeParameter.builder()
						.name("newJob").displayName("New Job Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("xml").displayName("XML Configuration")
						.type(ParameterType.STRING).defaultValue("")
						.description("Jenkins job config XML.").build(),
				NodeParameter.builder()
						.name("buildParameters").displayName("Build Parameters")
						.type(ParameterType.STRING).defaultValue("")
						.description("JSON object of build parameters.").build(),
				NodeParameter.builder()
						.name("reason").displayName("Reason")
						.type(ParameterType.STRING).defaultValue("")
						.description("Reason for quiet down.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max builds to return.").build()
		);
	}
}
