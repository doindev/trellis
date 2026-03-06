package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * uProc — data enrichment, validation, and processing tools via the uProc API.
 * Supports various "tools" for data operations (email validation, phone lookup, etc.).
 */
@Node(
		type = "uProc",
		displayName = "uProc",
		description = "Data enrichment and processing via uProc",
		category = "Miscellaneous",
		icon = "cog",
		credentials = {"uProcApi"},
		searchOnly = true
)
public class UProcNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.uproc.io/api/v2";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String email = context.getCredentialString("email", "");
			String tool = context.getParameter("tool", "");
			String value = context.getParameter("value", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> body = new HashMap<>();
					body.put("processor", tool);
					body.put("params", Map.of("value", value));

					var response = post(BASE_URL + "/process", body,
							Map.of("Authorization", "Bearer " + apiKey,
									"Content-Type", "application/json",
									"Email", email));
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
						.name("tool").displayName("Tool")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("uProc tool/processor name (e.g. 'checkDisposableEmail').").build(),
				NodeParameter.builder()
						.name("value").displayName("Value")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("Input value to process.").build()
		);
	}
}
