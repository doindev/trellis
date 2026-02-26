package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Telegram Trigger — receive webhook events from the Telegram Bot API.
 * Triggers workflow execution on incoming messages, edits, callback queries,
 * inline queries, polls and other update types.
 */
@Slf4j
@Node(
		type = "telegramTrigger",
		displayName = "Telegram Trigger",
		description = "Starts the workflow when a Telegram event is received",
		category = "Communication",
		icon = "telegram",
		credentials = {"telegramApi"},
		trigger = true
)
public class TelegramTriggerNode extends AbstractTriggerNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();

		if (inputData == null || inputData.isEmpty()) {
			log.debug("Telegram Trigger: no input data received");
			return NodeExecutionResult.success(List.of(createEmptyTriggerItem()));
		}

		List<String> events = context.getParameter("events", List.of());

		List<Map<String, Object>> results = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				Map<String, Object> data = unwrapJson(item);

				// Filter by event type if specified
				if (!events.isEmpty()) {
					boolean matchesEvent = false;
					for (String event : events) {
						if (data.containsKey(event)) {
							matchesEvent = true;
							break;
						}
					}
					if (!matchesEvent) {
						continue;
					}
				}

				results.add(createTriggerItem(data));
			} catch (Exception e) {
				log.error("Telegram Trigger error processing item", e);
				return handleError(context, "Telegram Trigger error: " + e.getMessage(), e);
			}
		}

		if (results.isEmpty()) {
			return NodeExecutionResult.empty();
		}

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("events").displayName("Events")
						.type(ParameterType.MULTI_OPTIONS)
						.description("The Telegram update events to listen for.")
						.options(List.of(
								ParameterOption.builder().name("Message").value("message")
										.description("Trigger on new incoming messages.").build(),
								ParameterOption.builder().name("Edited Message").value("edited_message")
										.description("Trigger when a message is edited.").build(),
								ParameterOption.builder().name("Channel Post").value("channel_post")
										.description("Trigger on new channel posts.").build(),
								ParameterOption.builder().name("Edited Channel Post").value("edited_channel_post")
										.description("Trigger when a channel post is edited.").build(),
								ParameterOption.builder().name("Callback Query").value("callback_query")
										.description("Trigger on callback queries from inline keyboards.").build(),
								ParameterOption.builder().name("Inline Query").value("inline_query")
										.description("Trigger on inline queries.").build(),
								ParameterOption.builder().name("Chosen Inline Result").value("chosen_inline_result")
										.description("Trigger when an inline result is chosen.").build(),
								ParameterOption.builder().name("Shipping Query").value("shipping_query")
										.description("Trigger on shipping queries.").build(),
								ParameterOption.builder().name("Pre Checkout Query").value("pre_checkout_query")
										.description("Trigger on pre-checkout queries.").build(),
								ParameterOption.builder().name("Poll").value("poll")
										.description("Trigger on poll state changes.").build(),
								ParameterOption.builder().name("Poll Answer").value("poll_answer")
										.description("Trigger when a user answers a poll.").build(),
								ParameterOption.builder().name("Chat Member").value("chat_member")
										.description("Trigger on chat member status changes.").build(),
								ParameterOption.builder().name("My Chat Member").value("my_chat_member")
										.description("Trigger when the bot's chat member status changes.").build()
						)).build()
		);
	}
}
