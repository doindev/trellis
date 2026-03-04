package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Netlify Trigger — starts workflow when Netlify deploy/form events occur.
 */
@Node(
		type = "netlifyTrigger",
		displayName = "Netlify Trigger",
		description = "Starts the workflow when a Netlify event occurs",
		category = "Miscellaneous",
		icon = "netlify",
		credentials = {"netlifyApi"},
		trigger = true,
		searchOnly = true,
		other = true
)
public class NetlifyTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.netlify.com/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessToken = context.getCredentialString("accessToken", "");
		String event = context.getParameter("event", "deployCreated");
		String siteId = context.getParameter("siteId", "");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		try {
			HttpResponse<String> response = get(BASE_URL + "/hooks?site_id=" + encode(siteId), headers);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("event", event);
			result.put("hooks", parseResponse(response));
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "Netlify Trigger error: " + e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("siteId").displayName("Site ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Netlify site to listen to.").required(true).build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("deployCreated")
						.options(List.of(
								ParameterOption.builder().name("Deploy Building").value("deployBuilding").build(),
								ParameterOption.builder().name("Deploy Created").value("deployCreated").build(),
								ParameterOption.builder().name("Deploy Failed").value("deployFailed").build(),
								ParameterOption.builder().name("Deploy Locked").value("deployLocked").build(),
								ParameterOption.builder().name("Deploy Unlocked").value("deployUnlocked").build(),
								ParameterOption.builder().name("Form Submitted").value("submissionCreated").build()
						)).build()
		);
	}
}
