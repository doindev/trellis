package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
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

@Slf4j
@Node(
	type = "linkedIn",
	displayName = "LinkedIn",
	description = "Post and manage LinkedIn content.",
	category = "Social Media",
	icon = "linkedIn",
	credentials = {"linkedInOAuth2Api"}
)
public class LinkedInNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.linkedin.com/v2";

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

		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("post")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Post").value("post").description("Create and manage posts").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("post"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a new post").build()
			)).build());

		// Post > Create: personUrn
		params.add(NodeParameter.builder()
			.name("personUrn").displayName("Person URN / Organization URN")
			.type(ParameterType.STRING).required(true)
			.description("The URN of the author (e.g. 'urn:li:person:ABC123' or 'urn:li:organization:12345').")
			.placeHolder("urn:li:person:ABC123")
			.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
			.build());

		// Post > Create: text
		params.add(NodeParameter.builder()
			.name("text").displayName("Text")
			.type(ParameterType.STRING).required(true)
			.typeOptions(Map.of("rows", 5))
			.description("The text content of the post.")
			.placeHolder("Check out our latest update!")
			.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
			.build());

		// Post > Create: visibility
		params.add(NodeParameter.builder()
			.name("visibility").displayName("Visibility")
			.type(ParameterType.OPTIONS).defaultValue("PUBLIC")
			.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Public").value("PUBLIC").description("Visible to everyone").build(),
				ParameterOption.builder().name("Connections Only").value("CONNECTIONS").description("Visible to connections only").build()
			)).build());

		// Post > Create: additional fields
		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("post"), "operation", List.of("create"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("mediaUrl").displayName("Media URL")
					.type(ParameterType.STRING)
					.description("URL of the media to share (link, image, article).").build(),
				NodeParameter.builder().name("mediaTitle").displayName("Media Title")
					.type(ParameterType.STRING)
					.description("Title for the shared media.").build(),
				NodeParameter.builder().name("mediaDescription").displayName("Media Description")
					.type(ParameterType.STRING)
					.description("Description for the shared media.").build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		String resource = context.getParameter("resource", "post");
		String operation = context.getParameter("operation", "create");

		try {
			Map<String, String> headers = new LinkedHashMap<>();
			headers.put("Authorization", "Bearer " + accessToken);
			headers.put("Content-Type", "application/json");
			headers.put("X-Restli-Protocol-Version", "2.0.0");

			if ("post".equals(resource) && "create".equals(operation)) {
				return createPost(context, headers);
			}

			return NodeExecutionResult.error("Unknown resource/operation: " + resource + "/" + operation);
		} catch (Exception e) {
			return handleError(context, "LinkedIn API error: " + e.getMessage(), e);
		}
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult createPost(NodeExecutionContext context, Map<String, String> headers) throws Exception {
		String personUrn = context.getParameter("personUrn", "");
		String text = context.getParameter("text", "");
		String visibility = context.getParameter("visibility", "PUBLIC");
		Map<String, Object> additional = context.getParameter("additionalFields", Map.of());

		String mediaUrl = additional.get("mediaUrl") != null ? String.valueOf(additional.get("mediaUrl")) : "";
		String mediaTitle = additional.get("mediaTitle") != null ? String.valueOf(additional.get("mediaTitle")) : "";
		String mediaDescription = additional.get("mediaDescription") != null ? String.valueOf(additional.get("mediaDescription")) : "";

		// Build UGC post body
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("author", personUrn);
		body.put("lifecycleState", "PUBLISHED");

		// Visibility
		Map<String, Object> visibilityObj = new LinkedHashMap<>();
		visibilityObj.put("com.linkedin.ugc.MemberNetworkVisibility", visibility);
		body.put("visibility", visibilityObj);

		// Specific content
		Map<String, Object> specificContent = new LinkedHashMap<>();
		Map<String, Object> shareContent = new LinkedHashMap<>();

		// Share commentary (text)
		Map<String, Object> shareCommentary = new LinkedHashMap<>();
		shareCommentary.put("text", text);
		shareContent.put("shareCommentary", shareCommentary);

		if (!mediaUrl.isEmpty()) {
			// Share with media
			shareContent.put("shareMediaCategory", "ARTICLE");
			Map<String, Object> media = new LinkedHashMap<>();
			media.put("status", "READY");
			Map<String, Object> originalUrl = new LinkedHashMap<>();
			originalUrl.put("url", mediaUrl);
			media.put("originalUrl", mediaUrl);
			if (!mediaTitle.isEmpty()) {
				media.put("title", Map.of("text", mediaTitle));
			}
			if (!mediaDescription.isEmpty()) {
				media.put("description", Map.of("text", mediaDescription));
			}
			shareContent.put("media", List.of(media));
		} else {
			shareContent.put("shareMediaCategory", "NONE");
		}

		specificContent.put("com.linkedin.ugc.ShareContent", shareContent);
		body.put("specificContent", specificContent);

		String url = BASE_URL + "/ugcPosts";
		HttpResponse<String> response = post(url, body, headers);

		if (response.statusCode() >= 400) {
			String responseBody = response.body() != null ? response.body() : "";
			if (responseBody.length() > 300) responseBody = responseBody.substring(0, 300) + "...";
			return NodeExecutionResult.error("LinkedIn API error (HTTP " + response.statusCode() + "): " + responseBody);
		}

		Map<String, Object> result = parseResponse(response);
		log.debug("LinkedIn post created successfully");
		return NodeExecutionResult.success(List.of(wrapInJson(result)));
	}
}
