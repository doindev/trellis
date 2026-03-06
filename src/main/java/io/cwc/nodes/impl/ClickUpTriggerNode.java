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
 * ClickUp Trigger Node -- webhook-based trigger that fires when events
 * occur in ClickUp (tasks created, updated, status changed, etc.).
 */
@Slf4j
@Node(
	type = "clickUpTrigger",
	displayName = "ClickUp Trigger",
	description = "Starts the workflow when events occur in ClickUp",
	category = "Project Management",
	icon = "clickUp",
	trigger = true,
	credentials = {"clickUpApi"}
)
public class ClickUpTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.clickup.com/api/v2";

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
				.name("event").displayName("Event")
				.type(ParameterType.OPTIONS).required(true).defaultValue("taskCreated")
				.options(List.of(
					ParameterOption.builder().name("Task Created").value("taskCreated").description("Trigger when a task is created").build(),
					ParameterOption.builder().name("Task Updated").value("taskUpdated").description("Trigger when a task is updated").build(),
					ParameterOption.builder().name("Task Deleted").value("taskDeleted").description("Trigger when a task is deleted").build(),
					ParameterOption.builder().name("Task Status Updated").value("taskStatusUpdated").description("Trigger when a task status changes").build(),
					ParameterOption.builder().name("Task Assignee Updated").value("taskAssigneeUpdated").description("Trigger when a task assignee changes").build(),
					ParameterOption.builder().name("Task Due Date Updated").value("taskDueDateUpdated").description("Trigger when a task due date changes").build(),
					ParameterOption.builder().name("Task Tag Updated").value("taskTagUpdated").description("Trigger when a task tag changes").build(),
					ParameterOption.builder().name("Task Moved").value("taskMoved").description("Trigger when a task is moved").build(),
					ParameterOption.builder().name("Task Comment Posted").value("taskCommentPosted").description("Trigger when a comment is posted on a task").build(),
					ParameterOption.builder().name("List Created").value("listCreated").description("Trigger when a list is created").build(),
					ParameterOption.builder().name("List Updated").value("listUpdated").description("Trigger when a list is updated").build(),
					ParameterOption.builder().name("List Deleted").value("listDeleted").description("Trigger when a list is deleted").build(),
					ParameterOption.builder().name("Folder Created").value("folderCreated").description("Trigger when a folder is created").build(),
					ParameterOption.builder().name("Folder Updated").value("folderUpdated").description("Trigger when a folder is updated").build(),
					ParameterOption.builder().name("Folder Deleted").value("folderDeleted").description("Trigger when a folder is deleted").build(),
					ParameterOption.builder().name("Space Created").value("spaceCreated").description("Trigger when a space is created").build(),
					ParameterOption.builder().name("Space Updated").value("spaceUpdated").description("Trigger when a space is updated").build(),
					ParameterOption.builder().name("Space Deleted").value("spaceDeleted").description("Trigger when a space is deleted").build(),
					ParameterOption.builder().name("Goal Created").value("goalCreated").description("Trigger when a goal is created").build(),
					ParameterOption.builder().name("Goal Updated").value("goalUpdated").description("Trigger when a goal is updated").build(),
					ParameterOption.builder().name("Goal Deleted").value("goalDeleted").description("Trigger when a goal is deleted").build()
				)).build(),

			NodeParameter.builder()
				.name("teamId").displayName("Team ID (Workspace)")
				.type(ParameterType.STRING).required(true)
				.description("The ClickUp workspace (team) ID to watch.")
				.build(),

			NodeParameter.builder()
				.name("webhookUrl").displayName("Webhook URL")
				.type(ParameterType.STRING)
				.description("The webhook URL to register with ClickUp. Leave empty for auto-configuration.")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String event = context.getParameter("event", "taskCreated");
		String teamId = context.getParameter("teamId", "");

		try {
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData != null && !inputData.isEmpty()) {
				// Webhook data received -- pass it through
				List<Map<String, Object>> results = new ArrayList<>();
				for (Map<String, Object> item : inputData) {
					Map<String, Object> enriched = new LinkedHashMap<>(unwrapJson(item));
					enriched.put("_triggerEvent", event);
					enriched.put("_triggerTimestamp", System.currentTimeMillis());
					results.add(wrapInJson(enriched));
				}
				return NodeExecutionResult.success(results);
			}

			// No webhook data -- attempt to register webhook
			String apiKey = String.valueOf(credentials.getOrDefault("apiKey", credentials.getOrDefault("accessToken", "")));
			String webhookUrl = context.getParameter("webhookUrl", "");

			if (!webhookUrl.isEmpty() && !apiKey.isEmpty()) {
				Map<String, String> headers = new LinkedHashMap<>();
				headers.put("Authorization", apiKey);
				headers.put("Content-Type", "application/json");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("endpoint", webhookUrl);
				body.put("events", List.of(event));

				HttpResponse<String> response = post(BASE_URL + "/team/" + encode(teamId) + "/webhook", body, headers);

				if (response.statusCode() >= 400) {
					log.warn("Failed to register ClickUp webhook: {}", response.body());
				} else {
					log.debug("ClickUp webhook registered for event: {}", event);
				}
			}

			// Return empty trigger item
			Map<String, Object> triggerData = new LinkedHashMap<>();
			triggerData.put("_triggerEvent", event);
			triggerData.put("_triggerTeamId", teamId);
			triggerData.put("_triggerTimestamp", System.currentTimeMillis());
			return NodeExecutionResult.success(List.of(wrapInJson(triggerData)));

		} catch (Exception e) {
			return handleError(context, "ClickUp Trigger error: " + e.getMessage(), e);
		}
	}
}
