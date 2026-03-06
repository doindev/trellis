package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * WordPress — interact with the WordPress REST API to manage posts, pages, and users.
 */
@Node(
		type = "wordpress",
		displayName = "WordPress",
		description = "Interact with WordPress REST API",
		category = "CMS / Website Builders",
		icon = "wordpress",
		credentials = {"wordpressApi"}
)
public class WordpressNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String url = context.getCredentialString("url", "").replaceAll("/+$", "");
		String username = context.getCredentialString("username", "");
		String password = context.getCredentialString("password", "");

		String baseUrl = url + "/wp-json/wp/v2";

		String resource = context.getParameter("resource", "post");
		String operation = context.getParameter("operation", "getAll");

		// Basic Auth: base64 encode username:password
		String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + auth);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "post" -> handlePost(context, headers, baseUrl, operation);
					case "page" -> handlePage(context, headers, baseUrl, operation);
					case "user" -> handleUser(context, headers, baseUrl, operation);
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

	private Map<String, Object> handlePost(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = buildPostPageBody(context);
				String format = context.getParameter("format", "");
				if (!format.isEmpty()) body.put("format", format);
				boolean sticky = toBoolean(context.getParameters().get("sticky"), false);
				if (sticky) body.put("sticky", true);
				String categories = context.getParameter("categories", "");
				if (!categories.isEmpty()) {
					body.put("categories", parseIntList(categories));
				}
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) {
					body.put("tags", parseIntList(tags));
				}
				HttpResponse<String> response = post(baseUrl + "/posts", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String postId = context.getParameter("postId", "");
				HttpResponse<String> response = get(baseUrl + "/posts/" + encode(postId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(baseUrl + "/posts");
				appendListQueryParams(context, url);
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String postId = context.getParameter("postId", "");
				Map<String, Object> body = buildPostPageBody(context);
				String format = context.getParameter("format", "");
				if (!format.isEmpty()) body.put("format", format);
				String stickyStr = context.getParameter("sticky", "");
				if (!stickyStr.isEmpty()) body.put("sticky", toBoolean(stickyStr, false));
				String categories = context.getParameter("categories", "");
				if (!categories.isEmpty()) {
					body.put("categories", parseIntList(categories));
				}
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) {
					body.put("tags", parseIntList(tags));
				}
				HttpResponse<String> response = post(baseUrl + "/posts/" + encode(postId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown post operation: " + operation);
		};
	}

	private Map<String, Object> handlePage(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = buildPostPageBody(context);
				String parentId = context.getParameter("parentId", "");
				if (!parentId.isEmpty()) body.put("parent", toInt(parentId, 0));
				int menuOrder = toInt(context.getParameters().get("menuOrder"), 0);
				if (menuOrder != 0) body.put("menu_order", menuOrder);
				HttpResponse<String> response = post(baseUrl + "/pages", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String pageId = context.getParameter("pageId", "");
				HttpResponse<String> response = get(baseUrl + "/pages/" + encode(pageId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(baseUrl + "/pages");
				appendListQueryParams(context, url);
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String pageId = context.getParameter("pageId", "");
				Map<String, Object> body = buildPostPageBody(context);
				String parentId = context.getParameter("parentId", "");
				if (!parentId.isEmpty()) body.put("parent", toInt(parentId, 0));
				String menuOrderStr = context.getParameter("menuOrder", "");
				if (!menuOrderStr.isEmpty()) body.put("menu_order", toInt(menuOrderStr, 0));
				HttpResponse<String> response = post(baseUrl + "/pages/" + encode(pageId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown page operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, Map<String, String> headers,
			String baseUrl, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("username", context.getParameter("username", ""));
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				body.put("email", context.getParameter("email", ""));
				String userPassword = context.getParameter("password", "");
				if (!userPassword.isEmpty()) body.put("password", userPassword);
				String userUrl = context.getParameter("url", "");
				if (!userUrl.isEmpty()) body.put("url", userUrl);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String roles = context.getParameter("roles", "");
				if (!roles.isEmpty()) {
					body.put("roles", Arrays.asList(roles.split("\\s*,\\s*")));
				}
				HttpResponse<String> response = post(baseUrl + "/users", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String userId = context.getParameter("userId", "");
				HttpResponse<String> response = get(baseUrl + "/users/" + encode(userId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				StringBuilder url = new StringBuilder(baseUrl + "/users");
				int perPage = toInt(context.getParameters().get("perPage"), 10);
				url.append("?per_page=").append(perPage);
				int page = toInt(context.getParameters().get("page"), 1);
				if (page > 1) url.append("&page=").append(page);
				String search = context.getParameter("search", "");
				if (!search.isEmpty()) url.append("&search=").append(encode(search));
				HttpResponse<String> response = get(url.toString(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String userId = context.getParameter("userId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String userPassword = context.getParameter("password", "");
				if (!userPassword.isEmpty()) body.put("password", userPassword);
				String userUrl = context.getParameter("url", "");
				if (!userUrl.isEmpty()) body.put("url", userUrl);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String roles = context.getParameter("roles", "");
				if (!roles.isEmpty()) {
					body.put("roles", Arrays.asList(roles.split("\\s*,\\s*")));
				}
				HttpResponse<String> response = post(baseUrl + "/users/" + encode(userId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> buildPostPageBody(NodeExecutionContext context) {
		Map<String, Object> body = new LinkedHashMap<>();
		String title = context.getParameter("title", "");
		if (!title.isEmpty()) body.put("title", title);
		String content = context.getParameter("content", "");
		if (!content.isEmpty()) body.put("content", content);
		String status = context.getParameter("status", "");
		if (!status.isEmpty()) body.put("status", status);
		String slug = context.getParameter("slug", "");
		if (!slug.isEmpty()) body.put("slug", slug);
		String excerpt = context.getParameter("excerpt", "");
		if (!excerpt.isEmpty()) body.put("excerpt", excerpt);
		String featuredMedia = context.getParameter("featuredMedia", "");
		if (!featuredMedia.isEmpty()) body.put("featured_media", toInt(featuredMedia, 0));
		String password = context.getParameter("postPassword", "");
		if (!password.isEmpty()) body.put("password", password);
		return body;
	}

	private void appendListQueryParams(NodeExecutionContext context, StringBuilder url) {
		int perPage = toInt(context.getParameters().get("perPage"), 10);
		url.append("?per_page=").append(perPage);
		int page = toInt(context.getParameters().get("page"), 1);
		if (page > 1) url.append("&page=").append(page);
		String status = context.getParameter("status", "");
		if (!status.isEmpty()) url.append("&status=").append(encode(status));
		String search = context.getParameter("search", "");
		if (!search.isEmpty()) url.append("&search=").append(encode(search));
		String orderBy = context.getParameter("orderBy", "");
		if (!orderBy.isEmpty()) url.append("&orderby=").append(encode(orderBy));
		String order = context.getParameter("order", "");
		if (!order.isEmpty()) url.append("&order=").append(encode(order));
	}

	private List<Integer> parseIntList(String csv) {
		List<Integer> result = new ArrayList<>();
		for (String part : csv.split("\\s*,\\s*")) {
			if (!part.isEmpty()) {
				result.add(toInt(part, 0));
			}
		}
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("post")
						.options(List.of(
								ParameterOption.builder().name("Post").value("post").build(),
								ParameterOption.builder().name("Page").value("page").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("postId").displayName("Post ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the post.").build(),
				NodeParameter.builder()
						.name("pageId").displayName("Page ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the page.").build(),
				NodeParameter.builder()
						.name("userId").displayName("User ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the user.").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("")
						.description("The title of the post or page.").build(),
				NodeParameter.builder()
						.name("content").displayName("Content")
						.type(ParameterType.STRING).defaultValue("")
						.description("The content of the post or page (HTML).").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS).defaultValue("draft")
						.options(List.of(
								ParameterOption.builder().name("Draft").value("draft").build(),
								ParameterOption.builder().name("Publish").value("publish").build(),
								ParameterOption.builder().name("Pending").value("pending").build(),
								ParameterOption.builder().name("Private").value("private").build()
						))
						.description("The publication status.").build(),
				NodeParameter.builder()
						.name("slug").displayName("Slug")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL slug for the post or page.").build(),
				NodeParameter.builder()
						.name("excerpt").displayName("Excerpt")
						.type(ParameterType.STRING).defaultValue("")
						.description("The excerpt of the post or page.").build(),
				NodeParameter.builder()
						.name("format").displayName("Format")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Standard").value("standard").build(),
								ParameterOption.builder().name("Aside").value("aside").build(),
								ParameterOption.builder().name("Chat").value("chat").build(),
								ParameterOption.builder().name("Gallery").value("gallery").build(),
								ParameterOption.builder().name("Link").value("link").build(),
								ParameterOption.builder().name("Image").value("image").build(),
								ParameterOption.builder().name("Quote").value("quote").build(),
								ParameterOption.builder().name("Status").value("status").build(),
								ParameterOption.builder().name("Video").value("video").build(),
								ParameterOption.builder().name("Audio").value("audio").build()
						))
						.description("Post format.").build(),
				NodeParameter.builder()
						.name("sticky").displayName("Sticky")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether the post is sticky.").build(),
				NodeParameter.builder()
						.name("categories").displayName("Categories")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of category IDs.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of tag IDs.").build(),
				NodeParameter.builder()
						.name("featuredMedia").displayName("Featured Media ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the featured media attachment.").build(),
				NodeParameter.builder()
						.name("postPassword").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Password to protect the post or page.").build(),
				NodeParameter.builder()
						.name("parentId").displayName("Parent Page ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the parent page (for hierarchical pages).").build(),
				NodeParameter.builder()
						.name("menuOrder").displayName("Menu Order")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("The order of the page in the menu.").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("")
						.description("Login name for the user.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Display name for the user.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address for the user.").build(),
				NodeParameter.builder()
						.name("password").displayName("User Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Password for the user.").build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of the user's website.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Description of the user.").build(),
				NodeParameter.builder()
						.name("roles").displayName("Roles")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of roles to assign to the user.").build(),
				NodeParameter.builder()
						.name("perPage").displayName("Per Page")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Number of items per page.").build(),
				NodeParameter.builder()
						.name("page").displayName("Page")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("Page number for pagination.").build(),
				NodeParameter.builder()
						.name("search").displayName("Search")
						.type(ParameterType.STRING).defaultValue("")
						.description("Search term to filter results.").build(),
				NodeParameter.builder()
						.name("orderBy").displayName("Order By")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Date").value("date").build(),
								ParameterOption.builder().name("Relevance").value("relevance").build(),
								ParameterOption.builder().name("ID").value("id").build(),
								ParameterOption.builder().name("Include").value("include").build(),
								ParameterOption.builder().name("Title").value("title").build(),
								ParameterOption.builder().name("Slug").value("slug").build(),
								ParameterOption.builder().name("Modified").value("modified").build()
						))
						.description("Field to sort results by.").build(),
				NodeParameter.builder()
						.name("order").displayName("Order")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Ascending").value("asc").build(),
								ParameterOption.builder().name("Descending").value("desc").build()
						))
						.description("Sort direction.").build()
		);
	}
}
