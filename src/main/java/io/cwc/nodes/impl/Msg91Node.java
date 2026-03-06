package io.cwc.nodes.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * MSG91 — send SMS via the MSG91 API.
 */
@Node(
		type = "msg91",
		displayName = "MSG91",
		description = "Send SMS via MSG91",
		category = "Communication",
		icon = "comment",
		credentials = {"msg91Api"}
)
public class Msg91Node extends AbstractApiNode {

	private static final String BASE_URL = "https://api.msg91.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String authKey = context.getCredentialString("authkey");
			String from = context.getParameter("from", "");
			String to = context.getParameter("to", "");
			String message = context.getParameter("message", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> qs = new LinkedHashMap<>();
					qs.put("authkey", authKey);
					qs.put("sender", from);
					qs.put("mobiles", to);
					qs.put("message", message);
					qs.put("route", "4");
					qs.put("country", "0");

					String url = buildUrl(BASE_URL + "/sendhttp.php", qs);
					var response = get(url, Map.of());
					results.add(wrapInJson(Map.of("response", response.body())));
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
						.name("from").displayName("Sender ID")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Sender ID for the SMS.").build(),
				NodeParameter.builder()
						.name("to").displayName("To")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("Recipient mobile number.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.required(true).description("SMS message content.").build()
		);
	}
}
