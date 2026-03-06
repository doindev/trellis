package io.cwc.nodes.impl.ai;

import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * OpenAI Assistant — interacts with the OpenAI Assistants API.
 * Supports creating assistants, sending messages to threads, listing assistants,
 * and managing threads and runs.
 */
@Slf4j
@Node(
		type = "openAiAssistant",
		displayName = "OpenAI Assistant",
		description = "Interact with OpenAI Assistants API",
		category = "AI / Miscellaneous",
		icon = "openai",
		credentials = {"openAiApi"}
)
public class OpenAiAssistantNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.openai.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey");
		if (apiKey == null || apiKey.isBlank()) {
			return NodeExecutionResult.error("OpenAI API key is required. Configure credentials.");
		}

		String resource = context.getParameter("resource", "assistant");
		String operation = context.getParameter("operation", "list");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		headers.put("OpenAI-Beta", "assistants=v2");

		try {
			return switch (resource) {
				case "assistant" -> handleAssistantOperations(context, operation, headers);
				case "thread" -> handleThreadOperations(context, operation, headers);
				case "message" -> handleMessageOperations(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "OpenAI Assistant API error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult handleAssistantOperations(NodeExecutionContext context,
			String operation, Map<String, String> headers) throws Exception {
		return switch (operation) {
			case "create" -> {
				String name = context.getParameter("name", "");
				String instructions = context.getParameter("instructions", "");
				String model = context.getParameter("model", "gpt-4o");

				Map<String, Object> body = new HashMap<>();
				body.put("model", model);
				if (!name.isBlank()) body.put("name", name);
				if (!instructions.isBlank()) body.put("instructions", instructions);

				HttpResponse<String> response = post(BASE_URL + "/assistants", body, headers);
				Map<String, Object> result = parseResponse(response);
				yield NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "delete" -> {
				String assistantId = context.getParameter("assistantId", "");
				if (assistantId.isBlank()) {
					yield NodeExecutionResult.error("Assistant ID is required for delete operation");
				}
				HttpResponse<String> response = delete(BASE_URL + "/assistants/" + assistantId, headers);
				Map<String, Object> result = parseResponse(response);
				yield NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "get" -> {
				String assistantId = context.getParameter("assistantId", "");
				if (assistantId.isBlank()) {
					yield NodeExecutionResult.error("Assistant ID is required for get operation");
				}
				HttpResponse<String> response = get(BASE_URL + "/assistants/" + assistantId, headers);
				Map<String, Object> result = parseResponse(response);
				yield NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "list" -> {
				HttpResponse<String> response = get(BASE_URL + "/assistants", headers);
				Map<String, Object> result = parseResponse(response);
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
				if (data != null) {
					List<Map<String, Object>> items = new ArrayList<>();
					for (Map<String, Object> assistant : data) {
						items.add(wrapInJson(assistant));
					}
					yield NodeExecutionResult.success(items);
				}
				yield NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			case "update" -> {
				String assistantId = context.getParameter("assistantId", "");
				if (assistantId.isBlank()) {
					yield NodeExecutionResult.error("Assistant ID is required for update operation");
				}
				String name = context.getParameter("name", "");
				String instructions = context.getParameter("instructions", "");
				String model = context.getParameter("model", "");

				Map<String, Object> body = new HashMap<>();
				if (!name.isBlank()) body.put("name", name);
				if (!instructions.isBlank()) body.put("instructions", instructions);
				if (!model.isBlank()) body.put("model", model);

				HttpResponse<String> response = post(BASE_URL + "/assistants/" + assistantId, body, headers);
				Map<String, Object> result = parseResponse(response);
				yield NodeExecutionResult.success(List.of(wrapInJson(result)));
			}
			default -> NodeExecutionResult.error("Unknown assistant operation: " + operation);
		};
	}

	private NodeExecutionResult handleThreadOperations(NodeExecutionContext context,
			String operation, Map<String, String> headers) throws Exception {
		if ("create".equals(operation)) {
			HttpResponse<String> response = post(BASE_URL + "/threads", Map.of(), headers);
			Map<String, Object> result = parseResponse(response);
			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		}
		return NodeExecutionResult.error("Unknown thread operation: " + operation);
	}

	private NodeExecutionResult handleMessageOperations(NodeExecutionContext context,
			String operation, Map<String, String> headers) throws Exception {
		if ("createAndRun".equals(operation)) {
			String assistantId = context.getParameter("assistantId", "");
			String message = context.getParameter("message", "");

			if (assistantId.isBlank()) {
				return NodeExecutionResult.error("Assistant ID is required");
			}
			if (message.isBlank()) {
				return NodeExecutionResult.error("Message content is required");
			}

			// Create thread
			HttpResponse<String> threadResponse = post(BASE_URL + "/threads", Map.of(), headers);
			Map<String, Object> thread = parseResponse(threadResponse);
			String threadId = (String) thread.get("id");

			// Add message to thread
			Map<String, Object> messageBody = new HashMap<>();
			messageBody.put("role", "user");
			messageBody.put("content", message);
			post(BASE_URL + "/threads/" + threadId + "/messages", messageBody, headers);

			// Create run
			Map<String, Object> runBody = new HashMap<>();
			runBody.put("assistant_id", assistantId);
			HttpResponse<String> runResponse = post(
					BASE_URL + "/threads/" + threadId + "/runs", runBody, headers);
			Map<String, Object> run = parseResponse(runResponse);
			String runId = (String) run.get("id");

			// Poll for completion
			String status = (String) run.get("status");
			int maxAttempts = 60;
			int attempt = 0;
			while (!"completed".equals(status) && !"failed".equals(status)
					&& !"cancelled".equals(status) && attempt < maxAttempts) {
				Thread.sleep(1000);
				HttpResponse<String> statusResponse = get(
						BASE_URL + "/threads/" + threadId + "/runs/" + runId, headers);
				run = parseResponse(statusResponse);
				status = (String) run.get("status");
				attempt++;
			}

			if (!"completed".equals(status)) {
				return NodeExecutionResult.error("Run did not complete. Final status: " + status);
			}

			// Get messages
			HttpResponse<String> messagesResponse = get(
					BASE_URL + "/threads/" + threadId + "/messages", headers);
			Map<String, Object> messagesResult = parseResponse(messagesResponse);

			Map<String, Object> resultData = new HashMap<>();
			resultData.put("threadId", threadId);
			resultData.put("runId", runId);
			resultData.put("status", status);
			resultData.put("messages", messagesResult.get("data"));

			return NodeExecutionResult.success(List.of(wrapInJson(resultData)));
		}
		return NodeExecutionResult.error("Unknown message operation: " + operation);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("assistant")
						.options(List.of(
								ParameterOption.builder().name("Assistant").value("assistant").build(),
								ParameterOption.builder().name("Thread").value("thread").build(),
								ParameterOption.builder().name("Message").value("message").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("list")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Create and Run").value("createAndRun")
										.description("Create a thread, add a message, and run the assistant").build()
						)).build(),
				NodeParameter.builder()
						.name("assistantId").displayName("Assistant ID")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("The ID of the assistant").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING)
						.defaultValue("")
						.description("Name for the assistant")
						.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"),
								"operation", List.of("create", "update")))).build(),
				NodeParameter.builder()
						.name("instructions").displayName("Instructions")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("")
						.description("System instructions for the assistant")
						.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"),
								"operation", List.of("create", "update")))).build(),
				NodeParameter.builder()
						.name("model").displayName("Model")
						.type(ParameterType.OPTIONS)
						.defaultValue("gpt-4o")
						.options(List.of(
								ParameterOption.builder().name("GPT-4o").value("gpt-4o").build(),
								ParameterOption.builder().name("GPT-4o Mini").value("gpt-4o-mini").build(),
								ParameterOption.builder().name("GPT-4 Turbo").value("gpt-4-turbo").build()
						))
						.displayOptions(Map.of("show", Map.of("resource", List.of("assistant"),
								"operation", List.of("create")))).build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 4))
						.defaultValue("")
						.description("The message to send to the assistant")
						.displayOptions(Map.of("show", Map.of("resource", List.of("message"),
								"operation", List.of("createAndRun")))).build()
		);
	}
}
