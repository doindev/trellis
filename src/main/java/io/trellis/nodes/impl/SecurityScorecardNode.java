package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * SecurityScorecard — retrieve security ratings and manage portfolios.
 */
@Node(
		type = "securityScorecard",
		displayName = "SecurityScorecard",
		description = "Get security ratings and manage portfolios",
		category = "Miscellaneous",
		icon = "securityScorecard",
		credentials = {"securityScorecardApi"},
		searchOnly = true
)
public class SecurityScorecardNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.securityscorecard.io";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String resource = context.getParameter("resource", "company");
		String operation = context.getParameter("operation", "getScorecard");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Token " + apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "company" -> handleCompany(context, headers, operation);
					case "industry" -> handleIndustry(context, headers, operation);
					case "portfolio" -> handlePortfolio(context, headers, operation);
					case "invite" -> {
						Map<String, Object> body = new LinkedHashMap<>();
						body.put("email", context.getParameter("email", ""));
						body.put("first_name", context.getParameter("firstName", ""));
						body.put("last_name", context.getParameter("lastName", ""));
						String message = context.getParameter("message", "");
						if (!message.isEmpty()) body.put("message", message);
						HttpResponse<String> response = post(BASE_URL + "/invitations", body, headers);
						yield parseResponse(response);
					}
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

	private Map<String, Object> handleCompany(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String domain = context.getParameter("domain", "");
		return switch (operation) {
			case "getScorecard" -> {
				HttpResponse<String> response = get(BASE_URL + "/companies/" + encode(domain), headers);
				yield parseResponse(response);
			}
			case "getFactorScores" -> {
				HttpResponse<String> response = get(BASE_URL + "/companies/" + encode(domain) + "/factors", headers);
				yield parseResponse(response);
			}
			case "getHistoricalScore" -> {
				HttpResponse<String> response = get(BASE_URL + "/companies/" + encode(domain) + "/history/factors/score", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown company operation: " + operation);
		};
	}

	private Map<String, Object> handleIndustry(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String industry = context.getParameter("industry", "technology");
		return switch (operation) {
			case "getScore" -> {
				HttpResponse<String> response = get(BASE_URL + "/industries/" + encode(industry) + "/score", headers);
				yield parseResponse(response);
			}
			case "getFactorScores" -> {
				HttpResponse<String> response = get(BASE_URL + "/industries/" + encode(industry) + "/history/factors", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown industry operation: " + operation);
		};
	}

	private Map<String, Object> handlePortfolio(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("portfolioName", ""));
				body.put("description", context.getParameter("description", ""));
				body.put("privacy", context.getParameter("privacy", "private"));
				HttpResponse<String> response = post(BASE_URL + "/portfolios", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String portfolioId = context.getParameter("portfolioId", "");
				HttpResponse<String> response = delete(BASE_URL + "/portfolios/" + encode(portfolioId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/portfolios", headers);
				yield parseResponse(response);
			}
			case "addCompany" -> {
				String portfolioId = context.getParameter("portfolioId", "");
				String domain = context.getParameter("domain", "");
				HttpResponse<String> response = put(BASE_URL + "/portfolios/" + encode(portfolioId) + "/companies/" + encode(domain), Map.of(), headers);
				yield parseResponse(response);
			}
			case "removeCompany" -> {
				String portfolioId = context.getParameter("portfolioId", "");
				String domain = context.getParameter("domain", "");
				HttpResponse<String> response = delete(BASE_URL + "/portfolios/" + encode(portfolioId) + "/companies/" + encode(domain), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown portfolio operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("company")
						.options(List.of(
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Industry").value("industry").build(),
								ParameterOption.builder().name("Invite").value("invite").build(),
								ParameterOption.builder().name("Portfolio").value("portfolio").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getScorecard")
						.options(List.of(
								ParameterOption.builder().name("Get Scorecard").value("getScorecard").build(),
								ParameterOption.builder().name("Get Factor Scores").value("getFactorScores").build(),
								ParameterOption.builder().name("Get Historical Score").value("getHistoricalScore").build(),
								ParameterOption.builder().name("Get Score").value("getScore").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Add Company").value("addCompany").build(),
								ParameterOption.builder().name("Remove Company").value("removeCompany").build()
						)).build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.description("Company domain (e.g., example.com).").build(),
				NodeParameter.builder()
						.name("industry").displayName("Industry")
						.type(ParameterType.OPTIONS).defaultValue("technology")
						.options(List.of(
								ParameterOption.builder().name("Food").value("food").build(),
								ParameterOption.builder().name("Healthcare").value("healthcare").build(),
								ParameterOption.builder().name("Manufacturing").value("manufacturing").build(),
								ParameterOption.builder().name("Retail").value("retail").build(),
								ParameterOption.builder().name("Technology").value("technology").build()
						)).build(),
				NodeParameter.builder()
						.name("portfolioId").displayName("Portfolio ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("portfolioName").displayName("Portfolio Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("privacy").displayName("Privacy")
						.type(ParameterType.OPTIONS).defaultValue("private")
						.options(List.of(
								ParameterOption.builder().name("Private").value("private").build(),
								ParameterOption.builder().name("Public").value("public").build()
						)).build(),
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
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
