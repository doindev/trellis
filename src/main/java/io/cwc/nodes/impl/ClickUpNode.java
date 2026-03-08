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
 * ClickUp Node -- manage checklists, comments, folders, goals, guests, lists,
 * spaces, tags, tasks, dependencies, time entries, and more in ClickUp.
 */
@Slf4j
@Node(
	type = "clickUp",
	displayName = "ClickUp",
	description = "Manage tasks, lists, spaces, folders, goals, time entries, and more in ClickUp",
	category = "Project Management",
	icon = "clickUp",
	credentials = {"clickUpApi"}
)
public class ClickUpNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.clickup.com/api/v2";

	@Override
	public List<NodeInput> getInputs() {
		return List.of(NodeInput.builder().name("main").displayName("Main Input").build());
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("task")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Checklist").value("checklist").description("Manage checklists").build(),
				ParameterOption.builder().name("Checklist Item").value("checklistItem").description("Manage checklist items").build(),
				ParameterOption.builder().name("Comment").value("comment").description("Manage comments").build(),
				ParameterOption.builder().name("Folder").value("folder").description("Manage folders").build(),
				ParameterOption.builder().name("Goal").value("goal").description("Manage goals").build(),
				ParameterOption.builder().name("Goal Key Result").value("goalKeyResult").description("Manage goal key results").build(),
				ParameterOption.builder().name("Guest").value("guest").description("Manage guests").build(),
				ParameterOption.builder().name("List").value("list").description("Manage lists").build(),
				ParameterOption.builder().name("Space").value("space").description("Manage spaces").build(),
				ParameterOption.builder().name("Tag").value("tag").description("Manage tags").build(),
				ParameterOption.builder().name("Task").value("task").description("Manage tasks").build(),
				ParameterOption.builder().name("Task Dependency").value("taskDependency").description("Manage task dependencies").build(),
				ParameterOption.builder().name("Task List").value("taskList").description("Add/remove tasks to/from lists").build(),
				ParameterOption.builder().name("Time Entry").value("timeEntry").description("Manage time entries").build(),
				ParameterOption.builder().name("Time Entry Tag").value("timeEntryTag").description("Manage time entry tags").build()
			)).build());

		// Checklist operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklist"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a checklist").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a checklist").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a checklist").build()
			)).build());

		// Checklist Item operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklistItem"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a checklist item").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a checklist item").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a checklist item").build()
			)).build());

		// Comment operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a comment").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a comment").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many comments").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a comment").build()
			)).build());

		// Folder operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a folder").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a folder").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a folder").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many folders").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a folder").build()
			)).build());

		// Goal operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("goal"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a goal").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a goal").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a goal").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many goals").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a goal").build()
			)).build());

		// Goal Key Result operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("goalKeyResult"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a key result").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a key result").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a key result").build()
			)).build());

		// Guest operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("get")
			.displayOptions(Map.of("show", Map.of("resource", List.of("guest"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Invite a guest").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Remove a guest").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a guest").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a guest").build()
			)).build());

		// List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a list").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a list").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a list").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many lists in a folder").build(),
				ParameterOption.builder().name("Get Folderless").value("getFolderless").description("Get folderless lists").build(),
				ParameterOption.builder().name("Members").value("member").description("Get list members").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a list").build()
			)).build());

		// Space operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("space"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a space").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a space").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a space").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many spaces").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a space").build()
			)).build());

		// Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("tag"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a tag").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a tag").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tags").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a tag").build()
			)).build());

		// Task operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a task").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a task").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a task").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many tasks").build(),
				ParameterOption.builder().name("Members").value("member").description("Get task members").build(),
				ParameterOption.builder().name("Set Custom Field").value("setCustomField").description("Set a custom field value").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a task").build()
			)).build());

		// Task Dependency operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskDependency"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a dependency").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a dependency").build()
			)).build());

		// Task List operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("add")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskList"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add a task to a list").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove a task from a list").build()
			)).build());

		// Time Entry operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a time entry").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a time entry").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a time entry").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many time entries").build(),
				ParameterOption.builder().name("Start").value("start").description("Start a time entry timer").build(),
				ParameterOption.builder().name("Stop").value("stop").description("Stop a time entry timer").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a time entry").build()
			)).build());

		// Time Entry Tag operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntryTag"))))
			.options(List.of(
				ParameterOption.builder().name("Add").value("add").description("Add tags to a time entry").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all time entry tags").build(),
				ParameterOption.builder().name("Remove").value("remove").description("Remove tags from a time entry").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("teamId").displayName("Team ID (Workspace)")
			.type(ParameterType.STRING).required(true)
			.description("The ID of the workspace/team.")
			.build());

		params.add(NodeParameter.builder()
			.name("spaceId").displayName("Space ID")
			.type(ParameterType.STRING)
			.description("The ID of the space.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("space", "folder", "list", "tag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("folderId").displayName("Folder ID")
			.type(ParameterType.STRING)
			.description("The ID of the folder.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("folder", "list"))))
			.build());

		params.add(NodeParameter.builder()
			.name("listId").displayName("List ID")
			.type(ParameterType.STRING)
			.description("The ID of the list.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("list", "task", "taskList"))))
			.build());

		params.add(NodeParameter.builder()
			.name("taskId").displayName("Task ID")
			.type(ParameterType.STRING)
			.description("The ID of the task.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task", "checklist", "comment", "taskDependency", "taskList"))))
			.build());

		params.add(NodeParameter.builder()
			.name("checklistId").displayName("Checklist ID")
			.type(ParameterType.STRING)
			.description("The ID of the checklist.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklist", "checklistItem"))))
			.build());

		params.add(NodeParameter.builder()
			.name("checklistItemId").displayName("Checklist Item ID")
			.type(ParameterType.STRING)
			.description("The ID of the checklist item.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("checklistItem"), "operation", List.of("delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("commentId").displayName("Comment ID")
			.type(ParameterType.STRING)
			.description("The ID of the comment.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("goalId").displayName("Goal ID")
			.type(ParameterType.STRING)
			.description("The ID of the goal.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("goal", "goalKeyResult"))))
			.build());

		params.add(NodeParameter.builder()
			.name("keyResultId").displayName("Key Result ID")
			.type(ParameterType.STRING)
			.description("The ID of the key result.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("goalKeyResult"), "operation", List.of("delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("guestId").displayName("Guest ID")
			.type(ParameterType.STRING)
			.description("The ID of the guest.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("guest"), "operation", List.of("delete", "get", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("timeEntryId").displayName("Time Entry ID")
			.type(ParameterType.STRING)
			.description("The ID of the time entry.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry", "timeEntryTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("dependsOnTaskId").displayName("Depends On Task ID")
			.type(ParameterType.STRING)
			.description("The ID of the task this task depends on.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("taskDependency"))))
			.build());

		params.add(NodeParameter.builder()
			.name("customFieldId").displayName("Custom Field ID")
			.type(ParameterType.STRING)
			.description("The ID of the custom field.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("setCustomField"))))
			.build());

		params.add(NodeParameter.builder()
			.name("customFieldValue").displayName("Custom Field Value")
			.type(ParameterType.STRING)
			.description("The value for the custom field.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("task"), "operation", List.of("setCustomField"))))
			.build());

		params.add(NodeParameter.builder()
			.name("name").displayName("Name")
			.type(ParameterType.STRING)
			.description("Name of the resource.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("description").displayName("Description")
			.type(ParameterType.STRING)
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("commentText").displayName("Comment Text")
			.type(ParameterType.STRING)
			.description("The text of the comment.")
			.typeOptions(Map.of("rows", 4))
			.displayOptions(Map.of("show", Map.of("resource", List.of("comment"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Email address of the guest.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("guest"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("duration").displayName("Duration (ms)")
			.type(ParameterType.NUMBER)
			.description("Duration of the time entry in milliseconds.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("startTime").displayName("Start Time (Unix ms)")
			.type(ParameterType.NUMBER)
			.description("Start time as Unix timestamp in milliseconds.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntry"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("tagNames").displayName("Tag Names (comma-separated)")
			.type(ParameterType.STRING)
			.description("Comma-separated tag names.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("timeEntryTag"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "task");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey",
				credentials.getOrDefault("accessToken", "")));

		try {
			Map<String, String> headers = authHeaders(apiKey);
			return switch (resource) {
				case "checklist" -> executeChecklist(context, operation, headers);
				case "checklistItem" -> executeChecklistItem(context, operation, headers);
				case "comment" -> executeComment(context, operation, headers);
				case "folder" -> executeFolder(context, operation, headers);
				case "goal" -> executeGoal(context, operation, headers);
				case "goalKeyResult" -> executeGoalKeyResult(context, operation, headers);
				case "guest" -> executeGuest(context, operation, headers);
				case "list" -> executeList(context, operation, headers);
				case "space" -> executeSpace(context, operation, headers);
				case "tag" -> executeTag(context, operation, headers);
				case "task" -> executeTask(context, operation, headers);
				case "taskDependency" -> executeTaskDependency(context, operation, headers);
				case "taskList" -> executeTaskList(context, operation, headers);
				case "timeEntry" -> executeTimeEntry(context, operation, headers);
				case "timeEntryTag" -> executeTimeEntryTag(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "ClickUp API error: " + e.getMessage(), e);
		}
	}

	// ========================= Checklist =========================

	private NodeExecutionResult executeChecklist(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String taskId = context.getParameter("taskId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/task/" + taskId + "/checklist", Map.of("name", name), headers);
				return toResult(response);
			}
			case "delete": {
				String checklistId = context.getParameter("checklistId", "");
				HttpResponse<String> response = delete(BASE_URL + "/checklist/" + checklistId, headers);
				return toDeleteResult(response, checklistId);
			}
			case "update": {
				String checklistId = context.getParameter("checklistId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = put(BASE_URL + "/checklist/" + checklistId, Map.of("name", name), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown checklist operation: " + operation);
		}
	}

	// ========================= Checklist Item =========================

	private NodeExecutionResult executeChecklistItem(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String checklistId = context.getParameter("checklistId", "");
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/checklist/" + checklistId + "/checklist_item", Map.of("name", name), headers);
				return toResult(response);
			}
			case "delete": {
				String itemId = context.getParameter("checklistItemId", "");
				HttpResponse<String> response = delete(BASE_URL + "/checklist/" + checklistId + "/checklist_item/" + itemId, headers);
				return toDeleteResult(response, itemId);
			}
			case "update": {
				String itemId = context.getParameter("checklistItemId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				HttpResponse<String> response = put(BASE_URL + "/checklist/" + checklistId + "/checklist_item/" + itemId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown checklistItem operation: " + operation);
		}
	}

	// ========================= Comment =========================

	private NodeExecutionResult executeComment(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String taskId = context.getParameter("taskId", "");
				String text = context.getParameter("commentText", "");
				HttpResponse<String> response = post(BASE_URL + "/task/" + taskId + "/comment", Map.of("comment_text", text), headers);
				return toResult(response);
			}
			case "delete": {
				String commentId = context.getParameter("commentId", "");
				HttpResponse<String> response = delete(BASE_URL + "/comment/" + commentId, headers);
				return toDeleteResult(response, commentId);
			}
			case "getAll": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/task/" + taskId + "/comment", headers);
				return toArrayResultFromKey(response, "comments");
			}
			case "update": {
				String commentId = context.getParameter("commentId", "");
				String text = context.getParameter("commentText", "");
				HttpResponse<String> response = put(BASE_URL + "/comment/" + commentId, Map.of("comment_text", text), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown comment operation: " + operation);
		}
	}

	// ========================= Folder =========================

	private NodeExecutionResult executeFolder(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String spaceId = context.getParameter("spaceId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/space/" + spaceId + "/folder", Map.of("name", name), headers);
				return toResult(response);
			}
			case "delete": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = delete(BASE_URL + "/folder/" + folderId, headers);
				return toDeleteResult(response, folderId);
			}
			case "get": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = get(BASE_URL + "/folder/" + folderId, headers);
				return toResult(response);
			}
			case "getAll": {
				String spaceId = context.getParameter("spaceId", "");
				HttpResponse<String> response = get(BASE_URL + "/space/" + spaceId + "/folder", headers);
				return toArrayResultFromKey(response, "folders");
			}
			case "update": {
				String folderId = context.getParameter("folderId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = put(BASE_URL + "/folder/" + folderId, Map.of("name", name), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown folder operation: " + operation);
		}
	}

	// ========================= Goal =========================

	private NodeExecutionResult executeGoal(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				String desc = context.getParameter("description", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				if (!desc.isEmpty()) body.put("description", desc);
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/goal", body, headers);
				return toResult(response);
			}
			case "delete": {
				String goalId = context.getParameter("goalId", "");
				HttpResponse<String> response = delete(BASE_URL + "/goal/" + goalId, headers);
				return toDeleteResult(response, goalId);
			}
			case "get": {
				String goalId = context.getParameter("goalId", "");
				HttpResponse<String> response = get(BASE_URL + "/goal/" + goalId, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/goal", headers);
				return toArrayResultFromKey(response, "goals");
			}
			case "update": {
				String goalId = context.getParameter("goalId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				putIfNotEmpty(body, "description", context.getParameter("description", ""));
				HttpResponse<String> response = put(BASE_URL + "/goal/" + goalId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown goal operation: " + operation);
		}
	}

	// ========================= Goal Key Result =========================

	private NodeExecutionResult executeGoalKeyResult(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String goalId = context.getParameter("goalId", "");
				String name = context.getParameter("name", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				HttpResponse<String> response = post(BASE_URL + "/goal/" + goalId + "/key_result", body, headers);
				return toResult(response);
			}
			case "delete": {
				String keyResultId = context.getParameter("keyResultId", "");
				HttpResponse<String> response = delete(BASE_URL + "/key_result/" + keyResultId, headers);
				return toDeleteResult(response, keyResultId);
			}
			case "update": {
				String keyResultId = context.getParameter("keyResultId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				HttpResponse<String> response = put(BASE_URL + "/key_result/" + keyResultId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown goalKeyResult operation: " + operation);
		}
	}

	// ========================= Guest =========================

	private NodeExecutionResult executeGuest(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		switch (operation) {
			case "create": {
				String email = context.getParameter("email", "");
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/guest", Map.of("email", email), headers);
				return toResult(response);
			}
			case "delete": {
				String guestId = context.getParameter("guestId", "");
				HttpResponse<String> response = delete(BASE_URL + "/team/" + teamId + "/guest/" + guestId, headers);
				return toDeleteResult(response, guestId);
			}
			case "get": {
				String guestId = context.getParameter("guestId", "");
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/guest/" + guestId, headers);
				return toResult(response);
			}
			case "update": {
				String guestId = context.getParameter("guestId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				HttpResponse<String> response = put(BASE_URL + "/team/" + teamId + "/guest/" + guestId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown guest operation: " + operation);
		}
	}

	// ========================= List =========================

	private NodeExecutionResult executeList(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String folderId = context.getParameter("folderId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/folder/" + folderId + "/list", Map.of("name", name), headers);
				return toResult(response);
			}
			case "delete": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = delete(BASE_URL + "/list/" + listId, headers);
				return toDeleteResult(response, listId);
			}
			case "get": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(BASE_URL + "/list/" + listId, headers);
				return toResult(response);
			}
			case "getAll": {
				String folderId = context.getParameter("folderId", "");
				HttpResponse<String> response = get(BASE_URL + "/folder/" + folderId + "/list", headers);
				return toArrayResultFromKey(response, "lists");
			}
			case "getFolderless": {
				String spaceId = context.getParameter("spaceId", "");
				HttpResponse<String> response = get(BASE_URL + "/space/" + spaceId + "/list", headers);
				return toArrayResultFromKey(response, "lists");
			}
			case "member": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(BASE_URL + "/list/" + listId + "/member", headers);
				return toArrayResultFromKey(response, "members");
			}
			case "update": {
				String listId = context.getParameter("listId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = put(BASE_URL + "/list/" + listId, Map.of("name", name), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown list operation: " + operation);
		}
	}

	// ========================= Space =========================

	private NodeExecutionResult executeSpace(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/space", Map.of("name", name), headers);
				return toResult(response);
			}
			case "delete": {
				String spaceId = context.getParameter("spaceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/space/" + spaceId, headers);
				return toDeleteResult(response, spaceId);
			}
			case "get": {
				String spaceId = context.getParameter("spaceId", "");
				HttpResponse<String> response = get(BASE_URL + "/space/" + spaceId, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/space", headers);
				return toArrayResultFromKey(response, "spaces");
			}
			case "update": {
				String spaceId = context.getParameter("spaceId", "");
				String name = context.getParameter("name", "");
				HttpResponse<String> response = put(BASE_URL + "/space/" + spaceId, Map.of("name", name), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown space operation: " + operation);
		}
	}

	// ========================= Tag =========================

	private NodeExecutionResult executeTag(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String spaceId = context.getParameter("spaceId", "");
		switch (operation) {
			case "create": {
				String name = context.getParameter("name", "");
				Map<String, Object> tag = Map.of("name", name);
				HttpResponse<String> response = post(BASE_URL + "/space/" + spaceId + "/tag", Map.of("tag", tag), headers);
				return toResult(response);
			}
			case "delete": {
				String name = context.getParameter("name", "");
				HttpResponse<String> response = delete(BASE_URL + "/space/" + spaceId + "/tag/" + encode(name), headers);
				return toDeleteResult(response, name);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/space/" + spaceId + "/tag", headers);
				return toArrayResultFromKey(response, "tags");
			}
			case "update": {
				String name = context.getParameter("name", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> tag = new LinkedHashMap<>(parseJson(additionalJson));
				tag.put("name", name);
				HttpResponse<String> response = put(BASE_URL + "/space/" + spaceId + "/tag/" + encode(name), Map.of("tag", tag), headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown tag operation: " + operation);
		}
	}

	// ========================= Task =========================

	private NodeExecutionResult executeTask(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String listId = context.getParameter("listId", "");
				String name = context.getParameter("name", "");
				String desc = context.getParameter("description", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				body.put("name", name);
				if (!desc.isEmpty()) body.put("description", desc);
				HttpResponse<String> response = post(BASE_URL + "/list/" + listId + "/task", body, headers);
				return toResult(response);
			}
			case "delete": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(BASE_URL + "/task/" + taskId, headers);
				return toDeleteResult(response, taskId);
			}
			case "get": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/task/" + taskId, headers);
				return toResult(response);
			}
			case "getAll": {
				String listId = context.getParameter("listId", "");
				HttpResponse<String> response = get(BASE_URL + "/list/" + listId + "/task", headers);
				return toArrayResultFromKey(response, "tasks");
			}
			case "member": {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(BASE_URL + "/task/" + taskId + "/member", headers);
				return toArrayResultFromKey(response, "members");
			}
			case "setCustomField": {
				String taskId = context.getParameter("taskId", "");
				String fieldId = context.getParameter("customFieldId", "");
				String value = context.getParameter("customFieldValue", "");
				HttpResponse<String> response = post(BASE_URL + "/task/" + taskId + "/field/" + fieldId, Map.of("value", value), headers);
				return toResult(response);
			}
			case "update": {
				String taskId = context.getParameter("taskId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(body, "name", context.getParameter("name", ""));
				putIfNotEmpty(body, "description", context.getParameter("description", ""));
				HttpResponse<String> response = put(BASE_URL + "/task/" + taskId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown task operation: " + operation);
		}
	}

	// ========================= Task Dependency =========================

	private NodeExecutionResult executeTaskDependency(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String taskId = context.getParameter("taskId", "");
		String dependsOn = context.getParameter("dependsOnTaskId", "");
		switch (operation) {
			case "create": {
				Map<String, Object> body = Map.of("depends_on", dependsOn);
				HttpResponse<String> response = post(BASE_URL + "/task/" + taskId + "/dependency", body, headers);
				return toResult(response);
			}
			case "delete": {
				HttpResponse<String> response = delete(BASE_URL + "/task/" + taskId + "/dependency?depends_on=" + dependsOn, headers);
				return toDeleteResult(response, taskId);
			}
			default:
				return NodeExecutionResult.error("Unknown taskDependency operation: " + operation);
		}
	}

	// ========================= Task List =========================

	private NodeExecutionResult executeTaskList(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String taskId = context.getParameter("taskId", "");
		String listId = context.getParameter("listId", "");
		switch (operation) {
			case "add": {
				HttpResponse<String> response = post(BASE_URL + "/list/" + listId + "/task/" + taskId, Map.of(), headers);
				return toResult(response);
			}
			case "remove": {
				HttpResponse<String> response = delete(BASE_URL + "/list/" + listId + "/task/" + taskId, headers);
				return toDeleteResult(response, taskId);
			}
			default:
				return NodeExecutionResult.error("Unknown taskList operation: " + operation);
		}
	}

	// ========================= Time Entry =========================

	private NodeExecutionResult executeTimeEntry(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		switch (operation) {
			case "create": {
				long duration = ((Number) context.getParameter("duration", 0)).longValue();
				long startTime = ((Number) context.getParameter("startTime", 0)).longValue();
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("duration", duration);
				body.put("start", startTime);
				if (!taskId.isEmpty()) body.put("tid", taskId);
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/time_entries", body, headers);
				return toResult(response);
			}
			case "delete": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = delete(BASE_URL + "/team/" + teamId + "/time_entries/" + timeEntryId, headers);
				return toDeleteResult(response, timeEntryId);
			}
			case "get": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/time_entries/" + timeEntryId, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/time_entries", headers);
				return toArrayResultFromKey(response, "data");
			}
			case "start": {
				String taskId = context.getParameter("taskId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!taskId.isEmpty()) body.put("tid", taskId);
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/time_entries/start", body, headers);
				return toResult(response);
			}
			case "stop": {
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/time_entries/stop", Map.of(), headers);
				return toResult(response);
			}
			case "update": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> body = new LinkedHashMap<>(parseJson(additionalJson));
				HttpResponse<String> response = put(BASE_URL + "/team/" + teamId + "/time_entries/" + timeEntryId, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown timeEntry operation: " + operation);
		}
	}

	// ========================= Time Entry Tag =========================

	private NodeExecutionResult executeTimeEntryTag(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String teamId = context.getParameter("teamId", "");
		switch (operation) {
			case "add": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				String tagNames = context.getParameter("tagNames", "");
				List<Map<String, String>> tags = new ArrayList<>();
				for (String t : tagNames.split(",")) {
					if (!t.trim().isEmpty()) tags.add(Map.of("name", t.trim()));
				}
				Map<String, Object> body = Map.of("tags", tags);
				HttpResponse<String> response = post(BASE_URL + "/team/" + teamId + "/time_entries/" + timeEntryId + "/tags", body, headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(BASE_URL + "/team/" + teamId + "/time_entries/tags", headers);
				return toArrayResultFromKey(response, "data");
			}
			case "remove": {
				String timeEntryId = context.getParameter("timeEntryId", "");
				String tagNames = context.getParameter("tagNames", "");
				List<Map<String, String>> tags = new ArrayList<>();
				for (String t : tagNames.split(",")) {
					if (!t.trim().isEmpty()) tags.add(Map.of("name", t.trim()));
				}
				Map<String, Object> body = Map.of("tags", tags);
				HttpResponse<String> response = deleteWithBody(BASE_URL + "/team/" + teamId + "/time_entries/" + timeEntryId + "/tags", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown timeEntryTag operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> authHeaders(String apiKey) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return clickUpError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResultFromKey(HttpResponse<String> response, String key) throws Exception {
		if (response.statusCode() >= 400) {
			return clickUpError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.get(key);
		if (data instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return clickUpError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult clickUpError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("ClickUp API error (HTTP " + response.statusCode() + "): " + body);
	}
}
