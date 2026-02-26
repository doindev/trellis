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

/**
 * Sentry.io Node -- manage issues, events, organizations, projects, releases,
 * and teams in Sentry via the Sentry API.
 */
@Slf4j
@Node(
	type = "sentryIo",
	displayName = "Sentry.io",
	description = "Manage issues and events in Sentry.io",
	category = "Development",
	icon = "sentryIo",
	credentials = {"sentryIoApi"}
)
public class SentryIoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://sentry.io/api/0";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("issue")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Event").value("event")
					.description("Manage Sentry events").build(),
				ParameterOption.builder().name("Issue").value("issue")
					.description("Manage Sentry issues").build(),
				ParameterOption.builder().name("Organization").value("organization")
					.description("Manage Sentry organizations").build(),
				ParameterOption.builder().name("Project").value("project")
					.description("Manage Sentry projects").build(),
				ParameterOption.builder().name("Release").value("release")
					.description("Manage Sentry releases").build(),
				ParameterOption.builder().name("Team").value("team")
					.description("Manage Sentry teams").build()
			)).build());

		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get an event by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many events").build()
			)).build());

		// Issue operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"))))
			.options(List.of(
				ParameterOption.builder().name("Delete").value("delete").description("Delete an issue").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an issue by ID").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many issues").build(),
				ParameterOption.builder().name("Update").value("update").description("Update an issue").build()
			)).build());

		// Organization operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an organization").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an organization").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many organizations").build()
			)).build());

		// Project operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a project").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a project").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a project").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many projects").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a project").build()
			)).build());

		// Release operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a release").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a release").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a release").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many releases").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a release").build()
			)).build());

		// Team operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a team").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a team").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a team").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many teams").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a team").build()
			)).build());

		// ---- Common identifiers ----
		params.add(NodeParameter.builder()
			.name("organizationSlug").displayName("Organization Slug")
			.type(ParameterType.STRING).required(true)
			.description("The slug of the organization.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event", "issue", "project", "release", "team"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectSlug").displayName("Project Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectSlugForIssue").displayName("Project Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("getAll"))))
			.build());

		// ---- Event parameters ----
		params.add(NodeParameter.builder()
			.name("eventId").displayName("Event ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("get"))))
			.build());

		// ---- Issue parameters ----
		params.add(NodeParameter.builder()
			.name("issueId").displayName("Issue ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("issueUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Resolved").value("resolved").build(),
						ParameterOption.builder().name("Unresolved").value("unresolved").build(),
						ParameterOption.builder().name("Ignored").value("ignored").build()
					)).build(),
				NodeParameter.builder().name("assignedTo").displayName("Assigned To")
					.type(ParameterType.STRING).description("User or team to assign to.").build(),
				NodeParameter.builder().name("hasSeen").displayName("Has Seen")
					.type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("isBookmarked").displayName("Is Bookmarked")
					.type(ParameterType.BOOLEAN).build()
			)).build());

		params.add(NodeParameter.builder()
			.name("issueQuery").displayName("Query")
			.type(ParameterType.STRING)
			.description("An optional search query to filter issues.")
			.placeHolder("is:unresolved")
			.displayOptions(Map.of("show", Map.of("resource", List.of("issue"), "operation", List.of("getAll"))))
			.build());

		// ---- Organization parameters ----
		params.add(NodeParameter.builder()
			.name("orgSlugForGet").displayName("Organization Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("get"))))
			.build());

		params.add(NodeParameter.builder()
			.name("orgName").displayName("Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("My Organization")
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("orgAgreeTerms").displayName("Agree to Terms")
			.type(ParameterType.BOOLEAN).defaultValue(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("organization"), "operation", List.of("create"))))
			.build());

		// ---- Project parameters ----
		params.add(NodeParameter.builder()
			.name("projectSlugForProject").displayName("Project Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamSlugForProject").displayName("Team Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectName").displayName("Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("My Project")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectPlatform").displayName("Platform")
			.type(ParameterType.STRING)
			.placeHolder("java")
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("projectUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("project"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("slug").displayName("Slug").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("platform").displayName("Platform").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("isBookmarked").displayName("Is Bookmarked").type(ParameterType.BOOLEAN).build()
			)).build());

		// ---- Release parameters ----
		params.add(NodeParameter.builder()
			.name("releaseVersion").displayName("Version")
			.type(ParameterType.STRING).required(true)
			.placeHolder("1.0.0")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseProjects").displayName("Projects")
			.type(ParameterType.STRING)
			.description("Comma-separated list of project slugs for this release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseRef").displayName("Ref")
			.type(ParameterType.STRING)
			.description("A commit reference (e.g., a commit SHA).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseUrl").displayName("URL")
			.type(ParameterType.STRING)
			.description("A URL that points to the release.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("releaseDateReleased").displayName("Date Released")
			.type(ParameterType.STRING)
			.description("Date released in ISO 8601 format.")
			.placeHolder("2024-01-15T00:00:00Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("release"), "operation", List.of("create", "update"))))
			.build());

		// ---- Team parameters ----
		params.add(NodeParameter.builder()
			.name("teamSlug").displayName("Team Slug")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamName").displayName("Name")
			.type(ParameterType.STRING).required(true)
			.placeHolder("My Team")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamSlugValue").displayName("Slug")
			.type(ParameterType.STRING)
			.description("A unique slug for the team.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("teamUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("team"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("slug").displayName("Slug").type(ParameterType.STRING).build()
			)).build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "issue");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			Map<String, String> headers = getAuthHeaders(credentials);

			return switch (resource) {
				case "event" -> executeEvent(context, operation, headers);
				case "issue" -> executeIssue(context, operation, headers);
				case "organization" -> executeOrganization(context, operation, headers);
				case "project" -> executeProject(context, operation, headers);
				case "release" -> executeRelease(context, operation, headers);
				case "team" -> executeTeam(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Sentry.io API error: " + e.getMessage(), e);
		}
	}

	// ========================= Event Operations =========================

	private NodeExecutionResult executeEvent(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String orgSlug = context.getParameter("organizationSlug", "");
		String projectSlug = context.getParameter("projectSlug", "");

		switch (operation) {
			case "get": {
				String eventId = context.getParameter("eventId", "");
				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/events/" + encode(eventId) + "/";
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/events/";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown event operation: " + operation);
		}
	}

	// ========================= Issue Operations =========================

	private NodeExecutionResult executeIssue(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "delete": {
				String issueId = context.getParameter("issueId", "");
				String url = BASE_URL + "/issues/" + encode(issueId) + "/";
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String issueId = context.getParameter("issueId", "");
				String url = BASE_URL + "/issues/" + encode(issueId) + "/";
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String orgSlug = context.getParameter("organizationSlug", "");
				String projectSlug = context.getParameter("projectSlugForIssue", "");
				String query = context.getParameter("issueQuery", "");

				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/issues/";
				if (query != null && !query.isEmpty()) {
					url += "?query=" + encode(query);
				}
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String issueId = context.getParameter("issueId", "");
				Map<String, Object> updateFields = context.getParameter("issueUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "status", updateFields.get("status"));
				putIfPresent(body, "assignedTo", updateFields.get("assignedTo"));
				if (updateFields.get("hasSeen") != null) {
					body.put("hasSeen", toBoolean(updateFields.get("hasSeen"), false));
				}
				if (updateFields.get("isBookmarked") != null) {
					body.put("isBookmarked", toBoolean(updateFields.get("isBookmarked"), false));
				}

				String url = BASE_URL + "/issues/" + encode(issueId) + "/";
				HttpResponse<String> response = put(url, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown issue operation: " + operation);
		}
	}

	// ========================= Organization Operations =========================

	private NodeExecutionResult executeOrganization(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String name = context.getParameter("orgName", "");
				boolean agreeTerms = toBoolean(context.getParameter("orgAgreeTerms", true), true);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("agreeTerms", agreeTerms);

				String url = BASE_URL + "/organizations/";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "get": {
				String orgSlug = context.getParameter("orgSlugForGet", "");
				String url = BASE_URL + "/organizations/" + encode(orgSlug) + "/";
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String url = BASE_URL + "/organizations/";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown organization operation: " + operation);
		}
	}

	// ========================= Project Operations =========================

	private NodeExecutionResult executeProject(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String orgSlug = context.getParameter("organizationSlug", "");

		switch (operation) {
			case "create": {
				String teamSlug = context.getParameter("teamSlugForProject", "");
				String name = context.getParameter("projectName", "");
				String platform = context.getParameter("projectPlatform", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "platform", platform);

				String url = BASE_URL + "/teams/" + encode(orgSlug) + "/" + encode(teamSlug) + "/projects/";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String projectSlug = context.getParameter("projectSlugForProject", "");
				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/";
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String projectSlug = context.getParameter("projectSlugForProject", "");
				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/";
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String url = BASE_URL + "/organizations/" + encode(orgSlug) + "/projects/";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String projectSlug = context.getParameter("projectSlugForProject", "");
				Map<String, Object> updateFields = context.getParameter("projectUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "slug", updateFields.get("slug"));
				putIfPresent(body, "platform", updateFields.get("platform"));
				if (updateFields.get("isBookmarked") != null) {
					body.put("isBookmarked", toBoolean(updateFields.get("isBookmarked"), false));
				}

				String url = BASE_URL + "/projects/" + encode(orgSlug) + "/" + encode(projectSlug) + "/";
				HttpResponse<String> response = put(url, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown project operation: " + operation);
		}
	}

	// ========================= Release Operations =========================

	private NodeExecutionResult executeRelease(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String orgSlug = context.getParameter("organizationSlug", "");

		switch (operation) {
			case "create": {
				String version = context.getParameter("releaseVersion", "");
				String projects = context.getParameter("releaseProjects", "");
				String ref = context.getParameter("releaseRef", "");
				String url = context.getParameter("releaseUrl", "");
				String dateReleased = context.getParameter("releaseDateReleased", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("version", version);
				if (projects != null && !projects.isEmpty()) {
					body.put("projects", Arrays.stream(projects.split(","))
						.map(String::trim).filter(s -> !s.isEmpty()).toList());
				}
				putIfPresent(body, "ref", ref);
				putIfPresent(body, "url", url);
				putIfPresent(body, "dateReleased", dateReleased);

				String apiUrl = BASE_URL + "/organizations/" + encode(orgSlug) + "/releases/";
				HttpResponse<String> response = post(apiUrl, body, headers);
				return toResult(response);
			}
			case "delete": {
				String version = context.getParameter("releaseVersion", "");
				String apiUrl = BASE_URL + "/organizations/" + encode(orgSlug) + "/releases/" + encode(version) + "/";
				HttpResponse<String> response = delete(apiUrl, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String version = context.getParameter("releaseVersion", "");
				String apiUrl = BASE_URL + "/organizations/" + encode(orgSlug) + "/releases/" + encode(version) + "/";
				HttpResponse<String> response = get(apiUrl, headers);
				return toResult(response);
			}
			case "getAll": {
				String apiUrl = BASE_URL + "/organizations/" + encode(orgSlug) + "/releases/";
				HttpResponse<String> response = get(apiUrl, headers);
				return toArrayResult(response);
			}
			case "update": {
				String version = context.getParameter("releaseVersion", "");
				String ref = context.getParameter("releaseRef", "");
				String url = context.getParameter("releaseUrl", "");
				String dateReleased = context.getParameter("releaseDateReleased", "");

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "ref", ref);
				putIfPresent(body, "url", url);
				putIfPresent(body, "dateReleased", dateReleased);

				String apiUrl = BASE_URL + "/organizations/" + encode(orgSlug) + "/releases/" + encode(version) + "/";
				HttpResponse<String> response = put(apiUrl, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown release operation: " + operation);
		}
	}

	// ========================= Team Operations =========================

	private NodeExecutionResult executeTeam(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String orgSlug = context.getParameter("organizationSlug", "");

		switch (operation) {
			case "create": {
				String name = context.getParameter("teamName", "");
				String slug = context.getParameter("teamSlugValue", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "slug", slug);

				String url = BASE_URL + "/organizations/" + encode(orgSlug) + "/teams/";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String teamSlug = context.getParameter("teamSlug", "");
				String url = BASE_URL + "/teams/" + encode(orgSlug) + "/" + encode(teamSlug) + "/";
				HttpResponse<String> response = delete(url, headers);
				return toDeleteResult(response);
			}
			case "get": {
				String teamSlug = context.getParameter("teamSlug", "");
				String url = BASE_URL + "/teams/" + encode(orgSlug) + "/" + encode(teamSlug) + "/";
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String url = BASE_URL + "/organizations/" + encode(orgSlug) + "/teams/";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			case "update": {
				String teamSlug = context.getParameter("teamSlug", "");
				Map<String, Object> updateFields = context.getParameter("teamUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "slug", updateFields.get("slug"));

				String url = BASE_URL + "/teams/" + encode(orgSlug) + "/" + encode(teamSlug) + "/";
				HttpResponse<String> response = put(url, body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown team operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(Map<String, Object> credentials) {
		String apiKey = String.valueOf(credentials.getOrDefault("apiKey", ""));
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiKey);
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private void putIfPresent(Map<String, Object> map, String key, Object value) {
		if (value != null && !String.valueOf(value).isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return sentryError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return sentryError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return sentryError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult sentryError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Sentry.io API error (HTTP " + response.statusCode() + "): " + body);
	}
}
