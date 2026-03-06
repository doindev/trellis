package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Airtop — automate browser sessions, scrape content, and interact with web pages using the Airtop API.
 */
@Node(
		type = "airtop",
		displayName = "Airtop",
		description = "Automate browser sessions and web interactions with Airtop",
		category = "Miscellaneous",
		icon = "airtop",
		credentials = {"airtopApi"},
		searchOnly = true
)
public class AirtopNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.airtop.ai/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "session");
		String operation = context.getParameter("operation", "create");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("api-key", apiKey);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "session" -> handleSession(context, headers, operation);
					case "window" -> handleWindow(context, headers, operation);
					case "extraction" -> handleExtraction(context, headers, operation);
					case "interaction" -> handleInteraction(context, headers, operation);
					case "file" -> handleFile(context, headers, operation);
					default -> throw new IllegalArgumentException("Unknown resource: " + resource);
				};
				results.add(wrapInJson(result));
			} catch (Exception e) {
				if (context.isContinueOnFail()) {
					return handleError(context, e.getMessage(), e);
				}
				throw new RuntimeException(e);
			}
		}

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleSession(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				Map<String, Object> configuration = new LinkedHashMap<>();

				int timeoutMinutes = toInt(context.getParameters().get("timeoutMinutes"), 10);
				configuration.put("timeoutMinutes", timeoutMinutes);

				String profileName = context.getParameter("profileName", "");
				if (!profileName.isEmpty()) {
					configuration.put("persistProfile", true);
					Map<String, Object> profileConfig = new LinkedHashMap<>();
					profileConfig.put("name", profileName);
					body.put("profile", profileConfig);
				}

				String proxy = context.getParameter("proxy", "none");
				if ("integrated".equals(proxy)) {
					Map<String, Object> proxyConfig = new LinkedHashMap<>();
					proxyConfig.put("type", "integrated");
					String country = context.getParameter("proxyCountry", "");
					if (!country.isEmpty()) proxyConfig.put("country", country);
					configuration.put("proxy", proxyConfig);
				} else if ("custom".equals(proxy)) {
					Map<String, Object> proxyConfig = new LinkedHashMap<>();
					proxyConfig.put("type", "custom");
					String proxyUrl = context.getParameter("proxyUrl", "");
					if (!proxyUrl.isEmpty()) proxyConfig.put("url", proxyUrl);
					configuration.put("proxy", proxyConfig);
				}

				boolean solveCaptchas = toBoolean(context.getParameters().get("solveCaptchas"), false);
				if (solveCaptchas) configuration.put("solveCaptchas", true);

				if (!configuration.isEmpty()) body.put("configuration", configuration);

				HttpResponse<String> response = post(BASE_URL + "/sessions", body, headers);
				yield parseResponse(response);
			}
			case "terminate" -> {
				String sessionId = context.getParameter("sessionId", "");
				HttpResponse<String> response = delete(BASE_URL + "/sessions/" + encode(sessionId), headers);
				yield parseResponse(response);
			}
			case "save" -> {
				String sessionId = context.getParameter("sessionId", "");
				String profileName = context.getParameter("profileName", "");
				HttpResponse<String> response = put(
						BASE_URL + "/sessions/" + encode(sessionId) + "/save-profile-on-termination/" + encode(profileName),
						Map.of(), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown session operation: " + operation);
		};
	}

	private Map<String, Object> handleWindow(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String sessionId = context.getParameter("sessionId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String url = context.getParameter("url", "");
				if (!url.isEmpty()) body.put("url", url);
				HttpResponse<String> response = post(BASE_URL + "/sessions/" + encode(sessionId) + "/windows", body, headers);
				yield parseResponse(response);
			}
			case "close" -> {
				String windowId = context.getParameter("windowId", "");
				HttpResponse<String> response = delete(
						BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId), headers);
				yield parseResponse(response);
			}
			case "list" -> {
				HttpResponse<String> response = get(
						BASE_URL + "/sessions/" + encode(sessionId) + "/windows", headers);
				yield parseResponse(response);
			}
			case "load" -> {
				String windowId = context.getParameter("windowId", "");
				String url = context.getParameter("url", "");
				Map<String, Object> body = Map.of("url", url);
				HttpResponse<String> response = post(
						BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId) + "/load",
						body, headers);
				yield parseResponse(response);
			}
			case "takeScreenshot" -> {
				String windowId = context.getParameter("windowId", "");
				HttpResponse<String> response = post(
						BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId) + "/take-screenshot",
						Map.of(), headers);
				yield parseResponse(response);
			}
			case "getLiveView" -> {
				String windowId = context.getParameter("windowId", "");
				HttpResponse<String> response = get(
						BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown window operation: " + operation);
		};
	}

	private Map<String, Object> handleExtraction(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String sessionId = context.getParameter("sessionId", "");
		String windowId = context.getParameter("windowId", "");
		String windowBase = BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId);

		return switch (operation) {
			case "query" -> {
				String prompt = context.getParameter("prompt", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("prompt", prompt);
				String outputSchema = context.getParameter("outputSchema", "");
				if (!outputSchema.isEmpty()) body.put("configuration", Map.of("outputSchema", parseJson(outputSchema)));
				HttpResponse<String> response = post(windowBase + "/query", body, headers);
				yield parseResponse(response);
			}
			case "scrape" -> {
				HttpResponse<String> response = post(windowBase + "/scrape-content", Map.of(), headers);
				yield parseResponse(response);
			}
			case "getPaginated" -> {
				String prompt = context.getParameter("prompt", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("prompt", prompt);
				HttpResponse<String> response = post(windowBase + "/query-paginated", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown extraction operation: " + operation);
		};
	}

	private Map<String, Object> handleInteraction(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String sessionId = context.getParameter("sessionId", "");
		String windowId = context.getParameter("windowId", "");
		String windowBase = BASE_URL + "/sessions/" + encode(sessionId) + "/windows/" + encode(windowId);

		return switch (operation) {
			case "click" -> {
				String elementDescription = context.getParameter("elementDescription", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("elementDescription", elementDescription);
				String clickType = context.getParameter("clickType", "left");
				if (!"left".equals(clickType)) body.put("type", clickType);
				HttpResponse<String> response = post(windowBase + "/click", body, headers);
				yield parseResponse(response);
			}
			case "type" -> {
				String elementDescription = context.getParameter("elementDescription", "");
				String text = context.getParameter("text", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("elementDescription", elementDescription);
				body.put("text", text);
				boolean pressEnterAfter = toBoolean(context.getParameters().get("pressEnterAfter"), false);
				if (pressEnterAfter) body.put("pressEnterAfter", true);
				HttpResponse<String> response = post(windowBase + "/type", body, headers);
				yield parseResponse(response);
			}
			case "hover" -> {
				String elementDescription = context.getParameter("elementDescription", "");
				Map<String, Object> body = Map.of("elementDescription", elementDescription);
				HttpResponse<String> response = post(windowBase + "/hover", body, headers);
				yield parseResponse(response);
			}
			case "scroll" -> {
				String direction = context.getParameter("scrollDirection", "down");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("direction", direction);
				int amount = toInt(context.getParameters().get("scrollAmount"), 0);
				if (amount > 0) body.put("amount", amount);
				HttpResponse<String> response = post(windowBase + "/scroll", body, headers);
				yield parseResponse(response);
			}
			case "fill" -> {
				String formData = context.getParameter("formData", "{}");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("formData", parseJson(formData));
				HttpResponse<String> response = post(windowBase + "/fill", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown interaction operation: " + operation);
		};
	}

	private Map<String, Object> handleFile(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = get(BASE_URL + "/files/" + encode(fileId), headers);
				yield parseResponse(response);
			}
			case "getMany" -> {
				StringBuilder url = new StringBuilder(BASE_URL + "/files");
				String sessionIds = context.getParameter("sessionIds", "");
				int limit = toInt(context.getParameters().get("limit"), 50);
				int offset = toInt(context.getParameters().get("offset"), 0);
				url.append("?limit=").append(limit).append("&offset=").append(offset);
				if (!sessionIds.isEmpty()) url.append("&sessionIds=").append(encode(sessionIds));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String fileId = context.getParameter("fileId", "");
				HttpResponse<String> response = delete(BASE_URL + "/files/" + encode(fileId), headers);
				yield parseResponse(response);
			}
			case "upload" -> {
				String fileUrl = context.getParameter("fileUrl", "");
				String fileName = context.getParameter("fileName", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!fileUrl.isEmpty()) body.put("url", fileUrl);
				if (!fileName.isEmpty()) body.put("fileName", fileName);
				HttpResponse<String> response = post(BASE_URL + "/files", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown file operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("session")
						.options(List.of(
								ParameterOption.builder().name("Extraction").value("extraction").build(),
								ParameterOption.builder().name("File").value("file").build(),
								ParameterOption.builder().name("Interaction").value("interaction").build(),
								ParameterOption.builder().name("Session").value("session").build(),
								ParameterOption.builder().name("Window").value("window").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Click").value("click").build(),
								ParameterOption.builder().name("Close").value("close").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Fill").value("fill").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get Live View").value("getLiveView").build(),
								ParameterOption.builder().name("Get Many").value("getMany").build(),
								ParameterOption.builder().name("Get Paginated").value("getPaginated").build(),
								ParameterOption.builder().name("Hover").value("hover").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("Load").value("load").build(),
								ParameterOption.builder().name("Query").value("query").build(),
								ParameterOption.builder().name("Save").value("save").build(),
								ParameterOption.builder().name("Scrape").value("scrape").build(),
								ParameterOption.builder().name("Scroll").value("scroll").build(),
								ParameterOption.builder().name("Take Screenshot").value("takeScreenshot").build(),
								ParameterOption.builder().name("Terminate").value("terminate").build(),
								ParameterOption.builder().name("Type").value("type").build(),
								ParameterOption.builder().name("Upload").value("upload").build()
						)).build(),
				NodeParameter.builder()
						.name("sessionId").displayName("Session ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Airtop session ID.").build(),
				NodeParameter.builder()
						.name("windowId").displayName("Window ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The browser window ID within a session.").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL to load in the browser window.").build(),
				NodeParameter.builder()
						.name("prompt").displayName("Prompt")
						.type(ParameterType.STRING).defaultValue("")
						.description("Query prompt for page extraction or paginated extraction.").build(),
				NodeParameter.builder()
						.name("outputSchema").displayName("Output Schema")
						.type(ParameterType.JSON).defaultValue("")
						.description("JSON schema for structured query output.").build(),
				NodeParameter.builder()
						.name("elementDescription").displayName("Element Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Natural language description of the element to interact with.").build(),
				NodeParameter.builder()
						.name("text").displayName("Text")
						.type(ParameterType.STRING).defaultValue("")
						.description("Text to type into an element.").build(),
				NodeParameter.builder()
						.name("pressEnterAfter").displayName("Press Enter After")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Press Enter key after typing text.").build(),
				NodeParameter.builder()
						.name("clickType").displayName("Click Type")
						.type(ParameterType.OPTIONS).defaultValue("left")
						.options(List.of(
								ParameterOption.builder().name("Left Click").value("left").build(),
								ParameterOption.builder().name("Double Click").value("double").build(),
								ParameterOption.builder().name("Right Click").value("right").build()
						))
						.description("Type of click action.").build(),
				NodeParameter.builder()
						.name("scrollDirection").displayName("Scroll Direction")
						.type(ParameterType.OPTIONS).defaultValue("down")
						.options(List.of(
								ParameterOption.builder().name("Down").value("down").build(),
								ParameterOption.builder().name("Up").value("up").build()
						))
						.description("Direction to scroll.").build(),
				NodeParameter.builder()
						.name("scrollAmount").displayName("Scroll Amount")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Amount to scroll (0 for auto).").build(),
				NodeParameter.builder()
						.name("formData").displayName("Form Data")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("Form data as JSON object.").build(),
				NodeParameter.builder()
						.name("profileName").displayName("Profile Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Browser profile name for session persistence.").build(),
				NodeParameter.builder()
						.name("timeoutMinutes").displayName("Timeout (Minutes)")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Session idle timeout in minutes.").build(),
				NodeParameter.builder()
						.name("proxy").displayName("Proxy")
						.type(ParameterType.OPTIONS).defaultValue("none")
						.options(List.of(
								ParameterOption.builder().name("None").value("none").build(),
								ParameterOption.builder().name("Airtop Integrated").value("integrated").build(),
								ParameterOption.builder().name("Custom").value("custom").build()
						))
						.description("Proxy configuration for the session.").build(),
				NodeParameter.builder()
						.name("proxyCountry").displayName("Proxy Country")
						.type(ParameterType.STRING).defaultValue("")
						.description("Country code for integrated proxy (e.g., US, GB).").build(),
				NodeParameter.builder()
						.name("proxyUrl").displayName("Proxy URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Custom proxy URL.").build(),
				NodeParameter.builder()
						.name("solveCaptchas").displayName("Solve Captchas")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Automatically solve CAPTCHAs.").build(),
				NodeParameter.builder()
						.name("fileId").displayName("File ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the file.").build(),
				NodeParameter.builder()
						.name("fileUrl").displayName("File URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of the file to upload.").build(),
				NodeParameter.builder()
						.name("fileName").displayName("File Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the uploaded file.").build(),
				NodeParameter.builder()
						.name("sessionIds").displayName("Session IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated session IDs to filter files.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Maximum number of results to return.").build(),
				NodeParameter.builder()
						.name("offset").displayName("Offset")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Number of results to skip.").build()
		);
	}
}
