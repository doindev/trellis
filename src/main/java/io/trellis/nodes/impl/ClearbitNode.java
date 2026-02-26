package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Clearbit — look up company and person data using the Clearbit API.
 */
@Node(
		type = "clearbit",
		displayName = "Clearbit",
		description = "Look up company and person data with Clearbit",
		category = "Miscellaneous",
		icon = "clearbit",
		credentials = {"clearbitApi"}
)
public class ClearbitNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "person");
		String operation = context.getParameter("operation", "enrich");

		String credentials = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + credentials);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "company" -> {
						String domain = context.getParameter("domain", "");
						yield switch (operation) {
							case "enrich" -> {
								HttpResponse<String> response = get("https://company.clearbit.com/v2/companies/find?domain=" + encode(domain), headers);
								yield parseResponse(response);
							}
							case "autocomplete" -> {
								String name = context.getParameter("companyName", "");
								HttpResponse<String> response = get("https://autocomplete.clearbit.com/v1/companies/suggest?query=" + encode(name), headers);
								yield parseResponse(response);
							}
							default -> throw new IllegalArgumentException("Unknown company operation: " + operation);
						};
					}
					case "person" -> {
						String email = context.getParameter("email", "");
						yield switch (operation) {
							case "enrich" -> {
								HttpResponse<String> response = get("https://person.clearbit.com/v2/people/find?email=" + encode(email), headers);
								yield parseResponse(response);
							}
							default -> throw new IllegalArgumentException("Unknown person operation: " + operation);
						};
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("person")
						.options(List.of(
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Person").value("person").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("enrich")
						.options(List.of(
								ParameterOption.builder().name("Autocomplete").value("autocomplete").build(),
								ParameterOption.builder().name("Enrich").value("enrich").build()
						)).build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address to look up.").build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.description("Company domain to look up (e.g., example.com).").build(),
				NodeParameter.builder()
						.name("companyName").displayName("Company Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Company name for autocomplete.").build()
		);
	}
}
