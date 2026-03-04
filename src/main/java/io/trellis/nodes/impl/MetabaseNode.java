package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Metabase — query alerts, databases, metrics, and questions using Metabase.
 */
@Node(
		type = "metabase",
		displayName = "Metabase",
		description = "Query and manage data in Metabase",
		category = "Miscellaneous",
		icon = "metabase",
		credentials = {"metabaseApi"},
		searchOnly = true
)
public class MetabaseNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "");
		if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		String sessionToken = context.getCredentialString("sessionToken", "");

		String resource = context.getParameter("resource", "question");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Metabase-Session", sessionToken);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "alert" -> handleAlert(context, baseUrl, headers, operation);
					case "database" -> handleDatabase(context, baseUrl, headers, operation);
					case "metric" -> handleMetric(context, baseUrl, headers, operation);
					case "question" -> handleQuestion(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleAlert(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String alertId = context.getParameter("alertId", "");
				HttpResponse<String> response = get(baseUrl + "/api/alert/" + encode(alertId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/api/alert/", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown alert operation: " + operation);
		};
	}

	private Map<String, Object> handleDatabase(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "add" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("engine", context.getParameter("engine", "postgres"));
				body.put("name", context.getParameter("databaseName", ""));
				Map<String, Object> details = new LinkedHashMap<>();
				details.put("host", context.getParameter("host", ""));
				details.put("port", context.getParameter("port", ""));
				details.put("user", context.getParameter("dbUser", ""));
				details.put("password", context.getParameter("dbPassword", ""));
				details.put("db", context.getParameter("dbName", ""));
				body.put("details", details);
				body.put("is_full_sync", toBoolean(context.getParameters().get("fullSync"), true));
				HttpResponse<String> response = post(baseUrl + "/api/database", body, headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/api/database/", headers);
				yield parseResponse(response);
			}
			case "getFields" -> {
				String databaseId = context.getParameter("databaseId", "");
				HttpResponse<String> response = get(baseUrl + "/api/database/" + encode(databaseId) + "/fields", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown database operation: " + operation);
		};
	}

	private Map<String, Object> handleMetric(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String metricId = context.getParameter("metricId", "");
				HttpResponse<String> response = get(baseUrl + "/api/metric/" + encode(metricId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/api/metric/", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown metric operation: " + operation);
		};
	}

	private Map<String, Object> handleQuestion(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String questionId = context.getParameter("questionId", "");
				HttpResponse<String> response = get(baseUrl + "/api/card/" + encode(questionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/api/card/", headers);
				yield parseResponse(response);
			}
			case "resultData" -> {
				String questionId = context.getParameter("questionId", "");
				String format = context.getParameter("format", "json");
				HttpResponse<String> response = post(baseUrl + "/api/card/" + encode(questionId) + "/query/" + encode(format), Map.of(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown question operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("question")
						.options(List.of(
								ParameterOption.builder().name("Alert").value("alert").build(),
								ParameterOption.builder().name("Database").value("database").build(),
								ParameterOption.builder().name("Metric").value("metric").build(),
								ParameterOption.builder().name("Question").value("question").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Fields").value("getFields").build(),
								ParameterOption.builder().name("Result Data").value("resultData").build()
						)).build(),
				NodeParameter.builder()
						.name("alertId").displayName("Alert ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("databaseId").displayName("Database ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("metricId").displayName("Metric ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("questionId").displayName("Question ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("engine").displayName("Engine")
						.type(ParameterType.OPTIONS).defaultValue("postgres")
						.options(List.of(
								ParameterOption.builder().name("H2").value("h2").build(),
								ParameterOption.builder().name("MongoDB").value("mongo").build(),
								ParameterOption.builder().name("MySQL").value("mysql").build(),
								ParameterOption.builder().name("PostgreSQL").value("postgres").build(),
								ParameterOption.builder().name("Redshift").value("redshift").build(),
								ParameterOption.builder().name("SQLite").value("sqlite").build()
						)).build(),
				NodeParameter.builder()
						.name("databaseName").displayName("Database Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("host").displayName("Host")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("port").displayName("Port")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dbUser").displayName("Database User")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dbPassword").displayName("Database Password")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dbName").displayName("Database")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("fullSync").displayName("Full Sync")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("format").displayName("Format")
						.type(ParameterType.OPTIONS).defaultValue("json")
						.options(List.of(
								ParameterOption.builder().name("CSV").value("csv").build(),
								ParameterOption.builder().name("JSON").value("json").build(),
								ParameterOption.builder().name("XLSX").value("xlsx").build()
						)).build()
		);
	}
}
