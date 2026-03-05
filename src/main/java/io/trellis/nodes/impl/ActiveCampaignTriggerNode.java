package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * ActiveCampaign Trigger — starts the workflow when events occur in ActiveCampaign.
 */
@Slf4j
@Node(
	type = "activeCampaignTrigger",
	displayName = "ActiveCampaign Trigger",
	description = "Starts the workflow when ActiveCampaign events occur",
	category = "Marketing",
	icon = "activeCampaign",
	credentials = {"activeCampaignApi"},
	trigger = true,
	searchOnly = true,
	triggerCategory = "Other"
)
public class ActiveCampaignTriggerNode extends AbstractApiNode {

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("events").displayName("Events")
				.type(ParameterType.MULTI_OPTIONS).required(true)
				.description("The events to listen for")
				.options(List.of(
					ParameterOption.builder().name("Contact Added").value("subscribe").build(),
					ParameterOption.builder().name("Contact Removed").value("unsubscribe").build(),
					ParameterOption.builder().name("Contact Updated").value("contact_update").build(),
					ParameterOption.builder().name("Deal Added").value("deal_add").build(),
					ParameterOption.builder().name("Deal Updated").value("deal_update").build(),
					ParameterOption.builder().name("Deal Task Completed").value("deal_task_complete").build(),
					ParameterOption.builder().name("Campaign Sent").value("sent").build(),
					ParameterOption.builder().name("Email Opened").value("open").build(),
					ParameterOption.builder().name("Link Clicked").value("click").build(),
					ParameterOption.builder().name("Email Bounced").value("bounce").build()
				)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiUrl = context.getCredentialString("apiUrl", "");
			String apiKey = context.getCredentialString("apiKey", "");
			String baseUrl = apiUrl.endsWith("/") ? apiUrl + "api/3" : apiUrl + "/api/3";

			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Api-Token", apiKey);
			headers.put("Content-Type", "application/json");

			List<String> events = context.getParameter("events", List.of());

			// List existing webhooks
			HttpResponse<String> response = get(baseUrl + "/webhooks", headers);
			Map<String, Object> parsed = parseResponse(response);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("events", events);
			result.put("webhooks", parsed.getOrDefault("webhooks", List.of()));
			result.put("_triggerTimestamp", System.currentTimeMillis());

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "ActiveCampaign Trigger error: " + e.getMessage(), e);
		}
	}
}
