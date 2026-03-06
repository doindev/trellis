package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Hunter — find and verify professional email addresses using the Hunter API.
 */
@Node(
		type = "hunter",
		displayName = "Hunter",
		description = "Find and verify professional email addresses",
		category = "Miscellaneous",
		icon = "hunter",
		credentials = {"hunterApi"},
		searchOnly = true
)
public class HunterNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.hunter.io/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "domainSearch");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "domainSearch" -> {
						String domain = context.getParameter("domain", "");
						int limit = toInt(context.getParameters().get("limit"), 100);
						String url = BASE_URL + "/domain-search?domain=" + encode(domain)
								+ "&limit=" + limit + "&api_key=" + encode(apiKey);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
						yield parseResponse(response);
					}
					case "emailFinder" -> {
						String domain = context.getParameter("domain", "");
						String firstName = context.getParameter("firstName", "");
						String lastName = context.getParameter("lastName", "");
						String url = BASE_URL + "/email-finder?domain=" + encode(domain)
								+ "&first_name=" + encode(firstName) + "&last_name=" + encode(lastName)
								+ "&api_key=" + encode(apiKey);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
						yield parseResponse(response);
					}
					case "emailVerifier" -> {
						String email = context.getParameter("email", "");
						String url = BASE_URL + "/email-verifier?email=" + encode(email)
								+ "&api_key=" + encode(apiKey);
						HttpResponse<String> response = get(url, Map.of("Accept", "application/json"));
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
						.type(ParameterType.OPTIONS).defaultValue("domainSearch")
						.options(List.of(
								ParameterOption.builder().name("Domain Search").value("domainSearch").build(),
								ParameterOption.builder().name("Email Finder").value("emailFinder").build(),
								ParameterOption.builder().name("Email Verifier").value("emailVerifier").build()
						)).build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.description("The domain to search (e.g., example.com).").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("First name for email finder.").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Last name for email finder.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address to verify.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return (1-100).").build()
		);
	}
}
