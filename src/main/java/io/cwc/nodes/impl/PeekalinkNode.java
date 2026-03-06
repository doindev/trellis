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
 * Peekalink — preview any URL to get title, description, image, etc.
 */
@Node(
		type = "peekalink",
		displayName = "Peekalink",
		description = "Preview any URL to get metadata",
		category = "Miscellaneous",
		icon = "eye",
		credentials = {"peekalinkApi"},
		searchOnly = true
)
public class PeekalinkNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.peekalink.io";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String url = context.getParameter("url", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					var response = post(BASE_URL, Map.of("link", url),
							Map.of("X-API-Key", apiKey, "Content-Type", "application/json"));
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
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.required(true)
						.description("URL to preview.").build()
		);
	}
}
