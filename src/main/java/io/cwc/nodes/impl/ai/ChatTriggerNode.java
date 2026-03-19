package io.cwc.nodes.impl.ai;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractTriggerNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Chat Trigger — webhook-based trigger for chat workflows.
 * Mimics n8n's "When chat message received" ChatTrigger node.
 *
 * When {@code public=true}, registers webhook endpoints:
 * - GET  /webhook/{path} — serves hosted chat page (hostedChat mode)
 * - POST /webhook/{path} — handles chat messages and session actions
 *
 * POST body format:
 * <pre>{
 *   "action": "sendMessage",
 *   "chatInput": "user message",
 *   "sessionId": "uuid"
 * }</pre>
 *
 * Also supports {@code action: "loadPreviousSession"} to retrieve chat history.
 *
 * Output to downstream nodes:
 * <pre>{
 *   "chatInput": "user message",
 *   "sessionId": "uuid",
 *   "action": "sendMessage"
 * }</pre>
 */
@Slf4j
@Node(
		type = "chatTrigger",
		displayName = "When Chat Message Received",
		description = "Runs the workflow when a chat message is received via webhook",
		category = "AI",
		icon = "comments",
		trigger = true,
		triggerFavorite = true
)
public class ChatTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				// ── Make Chat Publicly Available ──
				NodeParameter.builder()
						.name("public")
						.displayName("Make Chat Publicly Available")
						.description("Whether the chat should be publicly available via a webhook URL, " +
								"or only accessible through the manual chat interface.")
						.type(ParameterType.BOOLEAN)
						.defaultValue(false)
						.build(),

				// ── Mode ──
				NodeParameter.builder()
						.name("mode")
						.displayName("Mode")
						.type(ParameterType.OPTIONS)
						.defaultValue("hostedChat")
						.displayOptions(Map.of("show", Map.of("public", List.of(true))))
						.options(List.of(
								Map.of("name", "Hosted Chat",
										"value", "hostedChat",
										"description", "Chat on a page served by the application"),
								Map.of("name", "Embedded Chat",
										"value", "webhook",
										"description", "Chat through an embedded widget or by calling the webhook directly")
						))
						.build(),

				// ── Hosted chat notice ──
				NodeParameter.builder()
						.name("hostedChatNotice")
						.displayName("")
						.description("Chat will be live at the webhook URL once this workflow is published. " +
								"Executions will show up in the Executions tab.")
						.type(ParameterType.NOTICE)
						.displayOptions(Map.of("show", Map.of("public", List.of(true), "mode", List.of("hostedChat"))))
						.build(),

				// ── Embedded chat notice ──
				NodeParameter.builder()
						.name("embeddedChatNotice")
						.displayName("")
						.description("Send POST requests to the webhook URL with " +
								"{\"action\": \"sendMessage\", \"chatInput\": \"...\", \"sessionId\": \"...\"} " +
								"to interact with the chat. The chat will be live once this workflow is published.")
						.type(ParameterType.NOTICE)
						.displayOptions(Map.of("show", Map.of("public", List.of(true), "mode", List.of("webhook"))))
						.build(),

				// ── Chat Path ──
				NodeParameter.builder()
						.name("path")
						.displayName("Chat Path")
						.description("The URL path for the chat webhook. Uses /{contextPath}/{path} if a project context path is set, or /webhook/{workflowId}/{path} otherwise.")
						.type(ParameterType.STRING)
						.required(true)
						.defaultValue("chat")
						.placeHolder("chat")
						.displayOptions(Map.of("show", Map.of("public", List.of(true))))
						.build(),

				// ── Authentication ──
				NodeParameter.builder()
						.name("authentication")
						.displayName("Authentication")
						.description("How to authenticate requests to the chat endpoint.")
						.type(ParameterType.OPTIONS)
						.defaultValue("none")
						.displayOptions(Map.of("show", Map.of("public", List.of(true))))
						.options(List.of(
								Map.of("name", "None", "value", "none"),
								Map.of("name", "Basic Auth", "value", "basicAuth",
										"description", "Simple username and password")
						))
						.build(),

				// ── Initial Messages ──
				NodeParameter.builder()
						.name("initialMessages")
						.displayName("Initial Messages")
						.description("Default messages shown at the start of the chat, one per line.")
						.type(ParameterType.STRING)
						.typeOptions(Map.of("rows", 3))
						.defaultValue("Hi there!\nHow can I help you today?")
						.displayOptions(Map.of("show", Map.of("public", List.of(true), "mode", List.of("hostedChat"))))
						.build(),

				// ── Options (collection) ──
				NodeParameter.builder()
						.name("options")
						.displayName("Options")
						.type(ParameterType.COLLECTION)
						.defaultValue(Map.of())
						.displayOptions(Map.of("show", Map.of("public", List.of(true))))
						.nestedParameters(List.of(
								NodeParameter.builder()
										.name("allowedOrigins")
										.displayName("Allowed Origins (CORS)")
										.description("Comma-separated list of URLs allowed for cross-origin requests. " +
												"Use * (default) to allow all origins.")
										.type(ParameterType.STRING)
										.defaultValue("*")
										.build(),
								NodeParameter.builder()
										.name("allowFileUploads")
										.displayName("Allow File Uploads")
										.description("Whether to allow file uploads in the chat.")
										.type(ParameterType.BOOLEAN)
										.defaultValue(false)
										.build(),
								NodeParameter.builder()
										.name("allowedFilesMimeTypes")
										.displayName("Allowed File MIME Types")
										.description("Comma-separated list of allowed MIME types for file uploads. " +
												"Use * to allow all types.")
										.type(ParameterType.STRING)
										.defaultValue("*")
										.placeHolder("e.g. image/*, text/*, application/pdf")
										.build(),
								NodeParameter.builder()
										.name("inputPlaceholder")
										.displayName("Input Placeholder")
										.description("Placeholder text shown in the chat input field.")
										.type(ParameterType.STRING)
										.defaultValue("Type your question..")
										.placeHolder("e.g. Type your message here")
										.build(),
								NodeParameter.builder()
										.name("loadPreviousSession")
										.displayName("Load Previous Session")
										.description("Whether to load messages from a previous chat session.")
										.type(ParameterType.OPTIONS)
										.defaultValue("notSupported")
										.options(List.of(
												Map.of("name", "Off", "value", "notSupported",
														"description", "Loading previous session is turned off"),
												Map.of("name", "From Memory", "value", "memory",
														"description", "Load session messages from connected memory node")
										))
										.build(),
								NodeParameter.builder()
										.name("showWelcomeScreen")
										.displayName("Require Button Click to Start Chat")
										.description("Whether to show a welcome screen before the chat starts.")
										.type(ParameterType.BOOLEAN)
										.defaultValue(false)
										.build(),
								NodeParameter.builder()
										.name("title")
										.displayName("Title")
										.description("Shown at the top of the chat window.")
										.type(ParameterType.STRING)
										.defaultValue("Hi there!")
										.placeHolder("e.g. Welcome")
										.build(),
								NodeParameter.builder()
										.name("subtitle")
										.displayName("Subtitle")
										.description("Shown at the top of the chat, under the title.")
										.type(ParameterType.STRING)
										.defaultValue("Start a chat. We're here to help you 24/7.")
										.placeHolder("e.g. We're here for you")
										.build(),
								NodeParameter.builder()
										.name("getStarted")
										.displayName("Start Conversation Button Text")
										.description("Button text shown on the welcome screen.")
										.type(ParameterType.STRING)
										.defaultValue("New Conversation")
										.placeHolder("e.g. New Conversation")
										.build(),
								NodeParameter.builder()
										.name("responseMode")
										.displayName("Response Mode")
										.description("When and how to respond to the chat message.")
										.type(ParameterType.OPTIONS)
										.defaultValue("lastNode")
										.options(List.of(
												Map.of("name", "When Last Node Finishes", "value", "lastNode",
														"description", "Returns data of the last-executed node"),
												Map.of("name", "Using 'Respond to Webhook' Node", "value", "responseNode",
														"description", "Response defined in that node")
										))
										.build(),
								NodeParameter.builder()
										.name("customCss")
										.displayName("Custom Chat Styling")
										.description("Override the default styling of the chat interface with CSS.")
										.type(ParameterType.STRING)
										.typeOptions(Map.of("rows", 10))
										.defaultValue("")
										.build()
						))
						.build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		log.debug("Chat trigger fired for workflow: {}", context.getWorkflowId());

		List<Map<String, Object>> inputData = context.getInputData();

		// When triggered via webhook, input data contains the POST body
		if (inputData != null && !inputData.isEmpty()) {
			List<Map<String, Object>> outputItems = new ArrayList<>();
			for (Map<String, Object> item : inputData) {
				Map<String, Object> enriched = deepClone(item);
				Map<String, Object> json = unwrapJson(enriched);

				// Extract chat fields from webhook body
				Object bodyObj = json.get("body");
				if (bodyObj instanceof Map) {
					Map<String, Object> body = (Map<String, Object>) bodyObj;
					// Promote chatInput, sessionId, action to top level
					json.put("chatInput", body.getOrDefault("chatInput", ""));
					json.put("sessionId", body.getOrDefault("sessionId", ""));
					json.put("action", body.getOrDefault("action", "sendMessage"));
					if (body.containsKey("metadata")) {
						json.put("metadata", body.get("metadata"));
					}
				}

				json.put("executionMode", "chat_trigger");
				json.put("timestamp", Instant.now().toString());
				outputItems.add(wrapInJson(json));
			}
			return NodeExecutionResult.success(outputItems);
		}

		// Fallback: manual trigger with parameters
		String chatInput = context.getParameter("chatInput", "");
		String sessionId = context.getParameter("sessionId", "default");

		Map<String, Object> triggerData = new HashMap<>();
		triggerData.put("chatInput", chatInput);
		triggerData.put("sessionId", sessionId);
		triggerData.put("executionMode", "chat_trigger");
		triggerData.put("timestamp", Instant.now().toString());

		Map<String, Object> triggerItem = createTriggerItem(triggerData);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
