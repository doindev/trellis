package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * GetResponse Trigger — starts the workflow when GetResponse events occur.
 */
@Slf4j
@Node(
	type = "getResponseTrigger",
	displayName = "GetResponse Trigger",
	description = "Starts the workflow when GetResponse events occur",
	category = "Marketing",
	icon = "getResponse",
	credentials = {"getResponseApi"},
	trigger = true,
	searchOnly = true,
	triggerCategory = "Other"
)
public class GetResponseTriggerNode extends AbstractApiNode {

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
					ParameterOption.builder().name("Contact Subscribed").value("subscribe").build(),
					ParameterOption.builder().name("Contact Unsubscribed").value("unsubscribe").build(),
					ParameterOption.builder().name("Email Opened").value("open").build(),
					ParameterOption.builder().name("Email Clicked").value("click").build(),
					ParameterOption.builder().name("Contact Survey").value("survey").build()
				)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			List<String> events = context.getParameter("events", List.of());

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("events", events);
			result.put("_triggerTimestamp", System.currentTimeMillis());

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			return handleError(context, "GetResponse Trigger error: " + e.getMessage(), e);
		}
	}
}
