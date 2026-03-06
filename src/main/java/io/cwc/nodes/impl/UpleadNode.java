package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Uplead — enrich company and person data via the UpLead API.
 */
@Node(
		type = "uplead",
		displayName = "Uplead",
		description = "Enrich company and person data via UpLead",
		category = "Miscellaneous",
		icon = "address-card",
		credentials = {"upleadApi"},
		searchOnly = true
)
public class UpleadNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.uplead.com/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String resource = context.getParameter("resource", "company");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String url;
					if ("person".equals(resource)) {
						String email = context.getParameter("email", "");
						url = BASE_URL + "/person-search?email=" + encode(email);
					} else {
						String domain = context.getParameter("domain", "");
						url = BASE_URL + "/company-search?domain=" + encode(domain);
					}

					var response = get(url,
							Map.of("Authorization", apiKey));
					results.add(wrapInJson(parseResponse(response)));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("company")
						.options(List.of(
								ParameterOption.builder().name("Company").value("company").build(),
								ParameterOption.builder().name("Person").value("person").build()
						)).build(),
				NodeParameter.builder()
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.description("Company domain to look up.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address to look up person data.").build()
		);
	}
}
