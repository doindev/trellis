package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Discourse — interact with Discourse forum API to manage categories, groups, posts, and users.
 */
@Node(
		type = "discourse",
		displayName = "Discourse",
		description = "Interact with Discourse forum API",
		category = "CMS / Website Builders",
		icon = "discourse",
		credentials = {"discourseApi"}
)
public class DiscourseNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String baseUrl = context.getCredentialString("url", "").replaceAll("/+$", "");
		String apiKey = context.getCredentialString("apiKey", "");
		String apiUsername = context.getCredentialString("apiUsername", "system");

		String resource = context.getParameter("resource", "post");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Api-Key", apiKey);
		headers.put("Api-Username", apiUsername);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "category" -> handleCategory(context, headers, baseUrl, operation);
					case "group" -> handleGroup(context, headers, baseUrl, operation);
					case "post" -> handlePost(context, headers, baseUrl, operation);
					case "user" -> handleUser(context, headers, baseUrl, operation);
					case "userGroup" -> handleUserGroup(context, headers, baseUrl, operation);
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

	private Map<String, Object> handleCategory(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("name", ""));
				String color = context.getParameter("color", "0088CC");
				if (!color.isEmpty()) body.put("color", color);
				String textColor = context.getParameter("textColor", "FFFFFF");
				if (!textColor.isEmpty()) body.put("text_color", textColor);
				String parentCategoryId = context.getParameter("parentCategoryId", "");
				if (!parentCategoryId.isEmpty()) body.put("parent_category_id", toInt(parentCategoryId, 0));
				HttpResponse<String> response = post(baseUrl + "/categories.json", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String categoryId = context.getParameter("categoryId", "");
				HttpResponse<String> response = get(baseUrl + "/c/" + encode(categoryId) + "/show.json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/categories.json", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String categoryId = context.getParameter("categoryId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String color = context.getParameter("color", "");
				if (!color.isEmpty()) body.put("color", color);
				String textColor = context.getParameter("textColor", "");
				if (!textColor.isEmpty()) body.put("text_color", textColor);
				String parentCategoryId = context.getParameter("parentCategoryId", "");
				if (!parentCategoryId.isEmpty()) body.put("parent_category_id", toInt(parentCategoryId, 0));
				HttpResponse<String> response = put(baseUrl + "/categories/" + encode(categoryId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown category operation: " + operation);
		};
	}

	private Map<String, Object> handleGroup(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> group = new LinkedHashMap<>();
				group.put("name", context.getParameter("name", ""));
				String visibilityLevel = context.getParameter("visibilityLevel", "0");
				group.put("visibility_level", toInt(visibilityLevel, 0));
				String mentionableLevel = context.getParameter("mentionableLevel", "0");
				group.put("mentionable_level", toInt(mentionableLevel, 0));
				String messageableLevel = context.getParameter("messageableLevel", "0");
				group.put("messageable_level", toInt(messageableLevel, 0));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("group", group);
				HttpResponse<String> response = post(baseUrl + "/admin/groups.json", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String groupName = context.getParameter("name", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(groupName) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(baseUrl + "/groups.json", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String groupId = context.getParameter("groupId", "");
				Map<String, Object> group = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) group.put("name", name);
				String visibilityLevel = context.getParameter("visibilityLevel", "");
				if (!visibilityLevel.isEmpty()) group.put("visibility_level", toInt(visibilityLevel, 0));
				String mentionableLevel = context.getParameter("mentionableLevel", "");
				if (!mentionableLevel.isEmpty()) group.put("mentionable_level", toInt(mentionableLevel, 0));
				String messageableLevel = context.getParameter("messageableLevel", "");
				if (!messageableLevel.isEmpty()) group.put("messageable_level", toInt(messageableLevel, 0));
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("group", group);
				HttpResponse<String> response = put(baseUrl + "/groups/" + encode(groupId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown group operation: " + operation);
		};
	}

	private Map<String, Object> handlePost(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("title", context.getParameter("title", ""));
				body.put("raw", context.getParameter("content", ""));
				String categoryId = context.getParameter("categoryId", "");
				if (!categoryId.isEmpty()) body.put("category", toInt(categoryId, 0));
				String topicId = context.getParameter("topicId", "");
				if (!topicId.isEmpty()) body.put("topic_id", toInt(topicId, 0));
				String replyToPostNumber = context.getParameter("replyToPostNumber", "");
				if (!replyToPostNumber.isEmpty()) body.put("reply_to_post_number", toInt(replyToPostNumber, 0));
				HttpResponse<String> response = post(baseUrl + "/posts.json", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String postId = context.getParameter("postId", "");
				HttpResponse<String> response = get(baseUrl + "/posts/" + encode(postId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String topicId = context.getParameter("topicId", "");
				HttpResponse<String> response = get(baseUrl + "/t/" + encode(topicId) + ".json", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String postId = context.getParameter("postId", "");
				Map<String, Object> post = new LinkedHashMap<>();
				String content = context.getParameter("content", "");
				if (!content.isEmpty()) post.put("raw", content);
				String editReason = context.getParameter("editReason", "");
				if (!editReason.isEmpty()) post.put("edit_reason", editReason);
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("post", post);
				HttpResponse<String> response = put(baseUrl + "/posts/" + encode(postId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown post operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", context.getParameter("name", ""));
				body.put("email", context.getParameter("email", ""));
				body.put("password", context.getParameter("password", ""));
				body.put("username", context.getParameter("username", ""));
				boolean active = toBoolean(context.getParameters().get("active"), true);
				body.put("active", active);
				boolean approved = toBoolean(context.getParameters().get("approved"), true);
				body.put("approved", approved);
				HttpResponse<String> response = post(baseUrl + "/users.json", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String username = context.getParameter("username", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(username) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				String flag = context.getParameter("flag", "active");
				HttpResponse<String> response = get(baseUrl + "/admin/users/list/" + encode(flag) + ".json", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> handleUserGroup(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "add" -> {
				String groupId = context.getParameter("groupId", "");
				String usernames = context.getParameter("usernames", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("usernames", usernames);
				HttpResponse<String> response = put(baseUrl + "/groups/" + encode(groupId) + "/members.json", body, headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String groupId = context.getParameter("groupId", "");
				String usernames = context.getParameter("usernames", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("usernames", usernames);
				HttpResponse<String> response = deleteWithBody(baseUrl + "/groups/" + encode(groupId) + "/members.json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown userGroup operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("post")
						.options(List.of(
								ParameterOption.builder().name("Category").value("category").build(),
								ParameterOption.builder().name("Group").value("group").build(),
								ParameterOption.builder().name("Post").value("post").build(),
								ParameterOption.builder().name("User").value("user").build(),
								ParameterOption.builder().name("User Group").value("userGroup").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("categoryId").displayName("Category ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the category.").build(),
				NodeParameter.builder()
						.name("groupId").displayName("Group ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the group.").build(),
				NodeParameter.builder()
						.name("postId").displayName("Post ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the post.").build(),
				NodeParameter.builder()
						.name("topicId").displayName("Topic ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the topic.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the resource.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("The title of the post or topic.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The content/body of the post (raw Markdown).").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("")
						.description("The username of the user.").build(),
				NodeParameter.builder()
						.name("usernames").displayName("Usernames")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of usernames.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email address of the user.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("The password for the user.").build(),
				NodeParameter.builder()
						.name("color").displayName("Color")
						.type(ParameterType.STRING).defaultValue("0088CC")
						.description("The hex color code for the category.").build(),
				NodeParameter.builder()
						.name("textColor").displayName("Text Color")
						.type(ParameterType.STRING).defaultValue("FFFFFF")
						.description("The hex text color code for the category.").build(),
				NodeParameter.builder()
						.name("parentCategoryId").displayName("Parent Category ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the parent category.").build(),
				NodeParameter.builder()
						.name("visibilityLevel").displayName("Visibility Level")
						.type(ParameterType.OPTIONS).defaultValue("0")
						.options(List.of(
								ParameterOption.builder().name("Public").value("0").build(),
								ParameterOption.builder().name("Logged-In Users").value("1").build(),
								ParameterOption.builder().name("Members Only").value("2").build(),
								ParameterOption.builder().name("Staff").value("3").build(),
								ParameterOption.builder().name("Owners").value("4").build()
						))
						.description("Who can see the group.").build(),
				NodeParameter.builder()
						.name("mentionableLevel").displayName("Mentionable Level")
						.type(ParameterType.OPTIONS).defaultValue("0")
						.options(List.of(
								ParameterOption.builder().name("Nobody").value("0").build(),
								ParameterOption.builder().name("Only Members").value("1").build(),
								ParameterOption.builder().name("Members, Moderators and Admins").value("2").build(),
								ParameterOption.builder().name("Members and Everyone").value("3").build(),
								ParameterOption.builder().name("Everyone").value("99").build()
						))
						.description("Who can @mention the group.").build(),
				NodeParameter.builder()
						.name("messageableLevel").displayName("Messageable Level")
						.type(ParameterType.OPTIONS).defaultValue("0")
						.options(List.of(
								ParameterOption.builder().name("Nobody").value("0").build(),
								ParameterOption.builder().name("Only Members").value("1").build(),
								ParameterOption.builder().name("Members, Moderators and Admins").value("2").build(),
								ParameterOption.builder().name("Members and Everyone").value("3").build(),
								ParameterOption.builder().name("Everyone").value("99").build()
						))
						.description("Who can message the group.").build(),
				NodeParameter.builder()
						.name("replyToPostNumber").displayName("Reply to Post Number")
						.type(ParameterType.STRING).defaultValue("")
						.description("The post number to reply to within the topic.").build(),
				NodeParameter.builder()
						.name("editReason").displayName("Edit Reason")
						.type(ParameterType.STRING).defaultValue("")
						.description("The reason for editing the post.").build(),
				NodeParameter.builder()
						.name("active").displayName("Active")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether the user is active.").build(),
				NodeParameter.builder()
						.name("approved").displayName("Approved")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether the user is approved.").build(),
				NodeParameter.builder()
						.name("flag").displayName("User Flag")
						.type(ParameterType.OPTIONS).defaultValue("active")
						.options(List.of(
								ParameterOption.builder().name("Active").value("active").build(),
								ParameterOption.builder().name("New").value("new").build(),
								ParameterOption.builder().name("Staff").value("staff").build(),
								ParameterOption.builder().name("Suspended").value("suspended").build(),
								ParameterOption.builder().name("Blocked").value("blocked").build()
						))
						.description("Filter users by flag when listing all users.").build()
		);
	}
}
