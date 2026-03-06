package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
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
 * Trello Trigger Node -- webhook-based trigger that fires when events
 * occur on a Trello board (cards created, moved, updated, etc.).
 */
@Slf4j
@Node(
	type = "trelloTrigger",
	displayName = "Trello Trigger",
	description = "Starts the workflow when events occur on a Trello board",
	category = "Project Management",
	icon = "trello",
	trigger = true,
	credentials = {"trelloApi"}
)
public class TrelloTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.trello.com/1";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(); // trigger node has no inputs
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("boardId").displayName("Board ID")
				.type(ParameterType.STRING).required(true)
				.description("The ID of the Trello board to watch.")
				.build(),

			NodeParameter.builder()
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("all")
				.options(List.of(
					ParameterOption.builder().name("All Events").value("all").description("Trigger on any board event").build(),
					ParameterOption.builder().name("Card Created").value("createCard").description("Trigger when a card is created").build(),
					ParameterOption.builder().name("Card Updated").value("updateCard").description("Trigger when a card is updated").build(),
					ParameterOption.builder().name("Card Moved").value("updateCard:idList").description("Trigger when a card is moved to a different list").build(),
					ParameterOption.builder().name("Card Deleted").value("deleteCard").description("Trigger when a card is deleted").build(),
					ParameterOption.builder().name("Comment Added").value("commentCard").description("Trigger when a comment is added to a card").build(),
					ParameterOption.builder().name("List Created").value("createList").description("Trigger when a list is created").build(),
					ParameterOption.builder().name("List Updated").value("updateList").description("Trigger when a list is updated").build(),
					ParameterOption.builder().name("Checklist Created").value("addChecklistToCard").description("Trigger when a checklist is added to a card").build(),
					ParameterOption.builder().name("Label Created").value("createLabel").description("Trigger when a label is created").build(),
					ParameterOption.builder().name("Member Added").value("addMemberToBoard").description("Trigger when a member is added to the board").build()
				)).build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with Trello. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String boardId = context.getParameter("boardId", "");
		String event = context.getParameter("event", "all");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received -- filter by event type if specified
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					Map<String, Object> data = unwrapJson(item);
					String actionType = String.valueOf(data.getOrDefault("type", data.getOrDefault("action", "")));

					// Filter by event type unless "all"
					if (!"all".equals(event) && !actionType.startsWith(event.split(":")[0])) {
						continue;
					}

					Map<String, Object> enriched = new LinkedHashMap<>(data);
					enriched.put("_triggerEvent", event);
					enriched.put("_triggerBoardId", boardId);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());
					results.add(wrapInJson(enriched));
				}
				return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
			}

			// No webhook data -- attempt to register webhook
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
			String apiToken = String.valueOf(credentials.getOrDefault("apiToken", credentials.getOrDefault("accessToken", "")));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty() && !apiKey.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Content-Type", "application/json");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("callbackURL", webhookUrl);
				body.put("idModel", boardId);
				body.put("description", "CWC webhook for board " + boardId);

				String url = BASE_URL + "/webhooks?key=" + encode(apiKey) + "&token=" + encode(apiToken);
				HttpResponse<String> response = post(url, body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register Trello webhook: {}", response.body());
				} else {
					log.debug("Trello webhook registered for board: {}", boardId);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerBoardId", boardId);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "Trello Trigger error: " + e.getMessage(), e);
		}
	}
}
