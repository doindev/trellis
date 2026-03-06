package io.cwc.nodes.impl;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Node(
	type = "facebookTrigger",
	displayName = "Facebook Trigger",
	description = "Triggers when a Facebook webhook event is received.",
	category = "Social Media",
	icon = "facebook",
	credentials = {"facebookGraphApi"},
	trigger = true,
	searchOnly = true,
	triggerCategory = "Other"
)
public class FacebookTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("object").displayName("Object")
				.type(ParameterType.OPTIONS).required(true).defaultValue("page")
				.description("The Facebook object type to subscribe to.")
				.options(List.of(
					ParameterOption.builder().name("Page").value("page").description("Page events (posts, comments, etc.)").build(),
					ParameterOption.builder().name("User").value("user").description("User events").build(),
					ParameterOption.builder().name("Permissions").value("permissions").description("Permission changes").build(),
					ParameterOption.builder().name("Instagram").value("instagram").description("Instagram events via Facebook").build()
				)).build(),

			NodeParameter.builder()
				.name("field").displayName("Field")
				.type(ParameterType.OPTIONS)
				.description("The specific field/event to listen for.")
				.displayOptions(Map.of("show", Map.of("object", List.of("page"))))
				.options(List.of(
					ParameterOption.builder().name("Feed").value("feed").description("New posts in page feed").build(),
					ParameterOption.builder().name("Conversations").value("conversations").description("New conversations").build(),
					ParameterOption.builder().name("Messages").value("messages").description("New messages").build(),
					ParameterOption.builder().name("Mention").value("mention").description("Page mentions").build(),
					ParameterOption.builder().name("Ratings").value("ratings").description("Page ratings/reviews").build()
				)).build(),

			NodeParameter.builder()
				.name("verifyToken").displayName("Verify Token")
				.type(ParameterType.STRING)
				.description("The verify token used to validate the webhook subscription.")
				.placeHolder("my-verify-token")
				.build(),

			NodeParameter.builder()
				.name("appSecret").displayName("App Secret")
				.type(ParameterType.STRING)
				.description("The Facebook App Secret used to verify webhook payloads.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		String object = context.getParameter("object", "page");

		log.debug("Facebook Trigger fired for object type: {}", object);

		if (inputData != null && !inputData.isEmpty()) {
			// Process incoming webhook data
			List<Map<String, Object>> items = new ArrayList<>();
			for (Map<String, Object> data : inputData) {
				Map<String, Object> unwrapped = unwrapJson(data);
				Map<String, Object> triggerItem = createTriggerItem(unwrapped);
				triggerItem.put("object", object);
				items.add(triggerItem);
			}
			return NodeExecutionResult.success(items);
		}

		// No webhook data received, return empty trigger
		Map<String, Object> triggerItem = createTriggerItem(Map.of(
			"object", object,
			"message", "Waiting for Facebook webhook events"
		));
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
