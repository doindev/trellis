package io.trellis.nodes.impl;

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
 * Customer.io Trigger — starts the workflow when Customer.io webhook events occur.
 */
@Slf4j
@Node(
	type = "customerIoTrigger",
	displayName = "Customer.io Trigger",
	description = "Starts the workflow when Customer.io events occur",
	category = "Marketing",
	icon = "customerIo",
	credentials = {"customerIoApi"},
	trigger = true,
	searchOnly = true,
	triggerCategory = "Other"
)
public class CustomerIoTriggerNode extends AbstractApiNode {

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
					ParameterOption.builder().name("Customer Subscribed").value("customer.subscribed").build(),
					ParameterOption.builder().name("Customer Unsubscribed").value("customer.unsubscribed").build(),
					ParameterOption.builder().name("Email Delivered").value("email.delivered").build(),
					ParameterOption.builder().name("Email Opened").value("email.opened").build(),
					ParameterOption.builder().name("Email Clicked").value("email.clicked").build(),
					ParameterOption.builder().name("Email Bounced").value("email.bounced").build(),
					ParameterOption.builder().name("Email Complained").value("email.complained").build(),
					ParameterOption.builder().name("Push Delivered").value("push.delivered").build(),
					ParameterOption.builder().name("Push Opened").value("push.opened").build()
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
			return handleError(context, "Customer.io Trigger error: " + e.getMessage(), e);
		}
	}
}
