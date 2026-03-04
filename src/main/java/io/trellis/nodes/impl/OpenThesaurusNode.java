package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenThesaurus — look up synonyms via the OpenThesaurus API (German language).
 * No authentication required (public API).
 */
@Node(
		type = "openThesaurus",
		displayName = "OpenThesaurus",
		description = "Look up synonyms via OpenThesaurus (German)",
		category = "Miscellaneous",
		icon = "book",
		searchOnly = true
)
public class OpenThesaurusNode extends AbstractApiNode {

	private static final String BASE_URL = "https://www.openthesaurus.de/synonyme/search";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String word = context.getParameter("word", "");
			boolean similarTerms = toBoolean(context.getParameters().get("similarTerms"), false);
			boolean startsWith = toBoolean(context.getParameters().get("startsWith"), false);
			boolean supersynsets = toBoolean(context.getParameters().get("supersynsets"), false);
			boolean subsynsets = toBoolean(context.getParameters().get("subsynsets"), false);
			boolean baseforms = toBoolean(context.getParameters().get("baseforms"), false);

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String url = BASE_URL + "?q=" + encode(word) + "&format=application/json";
					if (similarTerms) url += "&similar=true";
					if (startsWith) url += "&startswith=true";
					if (supersynsets) url += "&supersynsets=true";
					if (subsynsets) url += "&subsynsets=true";
					if (baseforms) url += "&baseform=true";

					var response = get(url, Map.of());
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
						.name("word").displayName("Word")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("German word to look up synonyms for.").build(),
				NodeParameter.builder()
						.name("similarTerms").displayName("Similar Terms")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("startsWith").displayName("Starts With")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("supersynsets").displayName("Supersynsets")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("subsynsets").displayName("Subsynsets")
						.type(ParameterType.BOOLEAN).defaultValue(false).build(),
				NodeParameter.builder()
						.name("baseforms").displayName("Base Forms")
						.type(ParameterType.BOOLEAN).defaultValue(false).build()
		);
	}
}
