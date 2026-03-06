package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Brandfetch — retrieve brand assets (logos, colors, fonts) via the Brandfetch API.
 */
@Node(
		type = "brandfetch",
		displayName = "Brandfetch",
		description = "Retrieve brand assets via Brandfetch",
		category = "Miscellaneous",
		icon = "palette",
		credentials = {"brandfetchApi"},
		searchOnly = true
)
public class BrandfetchNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.brandfetch.io/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String domain = context.getParameter("domain", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					var response = get(BASE_URL + "/brands/" + encode(domain),
							Map.of("Authorization", "Bearer " + apiKey));
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
						.name("domain").displayName("Domain")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("Domain name to look up, e.g. 'google.com'.").build()
		);
	}
}
