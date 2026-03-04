package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Dropcontact — enrich and verify contact information using the Dropcontact API.
 */
@Node(
		type = "dropcontact",
		displayName = "Dropcontact",
		description = "Enrich and verify contact information",
		category = "Miscellaneous",
		icon = "dropcontact",
		credentials = {"dropcontactApi"},
		searchOnly = true
)
public class DropcontactNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.dropcontact.io";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");

		String operation = context.getParameter("operation", "enrich");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Access-Token", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "enrich" -> {
						Map<String, Object> contact = new LinkedHashMap<>();
						String email = context.getParameter("email", "");
						if (!email.isEmpty()) contact.put("email", email);
						String firstName = context.getParameter("firstName", "");
						if (!firstName.isEmpty()) contact.put("first_name", firstName);
						String lastName = context.getParameter("lastName", "");
						if (!lastName.isEmpty()) contact.put("last_name", lastName);
						String company = context.getParameter("company", "");
						if (!company.isEmpty()) contact.put("company", company);
						String website = context.getParameter("website", "");
						if (!website.isEmpty()) contact.put("website", website);
						String phone = context.getParameter("phone", "");
						if (!phone.isEmpty()) contact.put("phone", phone);

						Map<String, Object> body = new LinkedHashMap<>();
						body.put("data", List.of(contact));
						boolean siren = toBoolean(context.getParameters().get("siren"), false);
						if (siren) body.put("siren", true);
						String language = context.getParameter("language", "en");
						body.put("language", language);

						HttpResponse<String> response = post(BASE_URL + "/batch", body, headers);
						yield parseResponse(response);
					}
					case "fetchRequest" -> {
						String requestId = context.getParameter("requestId", "");
						HttpResponse<String> response = get(BASE_URL + "/batch/" + encode(requestId), headers);
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
						.type(ParameterType.OPTIONS).defaultValue("enrich")
						.options(List.of(
								ParameterOption.builder().name("Enrich").value("enrich").build(),
								ParameterOption.builder().name("Fetch Request").value("fetchRequest").build()
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
						.name("company").displayName("Company")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("website").displayName("Website")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("siren").displayName("SIREN Enrichment")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Enable French company enrichment (SIREN/SIRET).").build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.OPTIONS).defaultValue("en")
						.options(List.of(
								ParameterOption.builder().name("English").value("en").build(),
								ParameterOption.builder().name("French").value("fr").build()
						)).build(),
				NodeParameter.builder()
						.name("requestId").displayName("Request ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Request ID from a previous enrichment batch.").build()
		);
	}
}
