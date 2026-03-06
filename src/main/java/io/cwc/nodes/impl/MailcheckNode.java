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
 * Mailcheck — validate email addresses via the Mailcheck API.
 */
@Node(
		type = "mailcheck",
		displayName = "Mailcheck",
		description = "Validate email addresses via Mailcheck",
		category = "Marketing",
		icon = "envelope",
		credentials = {"mailcheckApi"},
		searchOnly = true
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
