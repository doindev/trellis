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
 * Mailcheck — validate email addresses via the Mailcheck API.
 */
@Node(
		type = "mailcheck",
		displayName = "Mailcheck",
		description = "Validate email addresses via Mailcheck",
		category = "Marketing",
		icon = "envelope",
		credentials = {"mailcheckApi"}
)
public class MailcheckNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.mailcheck.co/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String email = context.getParameter("email", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					var response = post(BASE_URL + "/singleEmail:check",
							Map.of("email", email),
							Map.of("Authorization", "Bearer " + apiKey, "Content-Type", "application/json"));
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
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Email address to validate.").build()
		);
	}
}
