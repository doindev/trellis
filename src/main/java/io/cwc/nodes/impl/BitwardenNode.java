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

@Slf4j
@Node(
	type = "bitwarden",
	displayName = "Bitwarden",
	description = "Manage secrets, collections, groups, members, and events in Bitwarden.",
	category = "Miscellaneous",
	icon = "bitwarden",
	credentials = {"bitwardenApi"},
	searchOnly = true
)
public class BitwardenNode extends AbstractApiNode {

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("collection")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Collection").value("collection").description("Manage collections").build(),
				ParameterOption.builder().name("Event").value("event").description("View organization events").build(),
				ParameterOption.builder().name("Group").value("group").description("Manage groups").build(),
				ParameterOption.builder().name("Member").value("member").description("Manage organization members").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addCollectionParameters(params);
		addEventParameters(params);
		addGroupParameters(params);
		addMemberParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Collection operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a collection").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a collection").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a collection").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all collections").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a collection").build()
			)).build());

		// Event operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all events").build()
			)).build());

		// Group operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a group").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a group").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a group").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all groups").build(),
				ParameterOption.builder().name("Get Members").value("getMember").description("Get group members").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a group").build(),
				ParameterOption.builder().name("Update Members").value("updateMembers").description("Update group members").build()
			)).build());

		// Member operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Invite a member").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Remove a member").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a member").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all members").build(),
				ParameterOption.builder().name("Get Groups").value("getGroups").description("Get member groups").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a member").build(),
				ParameterOption.builder().name("Update Groups").value("updateGroups").description("Update member groups").build()
			)).build());
	}

	// ========================= Collection Parameters =========================

	private void addCollectionParameters(List<NodeParameter> params) {
		// Collection > Create: name
		params.add(NodeParameter.builder()
			.name("collectionName").displayName("Collection Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"), "operation", List.of("create"))))
			.build());

		// Collection > Create: externalId
		params.add(NodeParameter.builder()
			.name("externalId").displayName("External ID").type(ParameterType.STRING)
			.description("The external identifier for reference.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"), "operation", List.of("create"))))
			.build());

		// Collection > Create: groups (JSON)
		params.add(NodeParameter.builder()
			.name("collectionGroups").displayName("Groups (JSON)").type(ParameterType.STRING)
			.description("JSON array of group associations, e.g. [{\"id\":\"groupId\",\"readOnly\":false}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"), "operation", List.of("create"))))
			.build());

		// Collection > Get/Delete/Update: collectionId
		params.add(NodeParameter.builder()
			.name("collectionId").displayName("Collection ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"), "operation", List.of("get", "delete", "update"))))
			.build());

		// Collection > Update: update fields
		params.add(NodeParameter.builder()
			.name("collectionUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("collection"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("externalId").displayName("External ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("groups").displayName("Groups (JSON)").type(ParameterType.STRING)
					.description("JSON array of group associations").build()
			)).build());
	}

	// ========================= Event Parameters =========================

	private void addEventParameters(List<NodeParameter> params) {
		// Event > GetAll: start date
		params.add(NodeParameter.builder()
			.name("eventStart").displayName("Start Date").type(ParameterType.STRING)
			.description("Start date for filtering events (ISO 8601).")
			.placeHolder("2024-01-01T00:00:00Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("getAll"))))
			.build());

		// Event > GetAll: end date
		params.add(NodeParameter.builder()
			.name("eventEnd").displayName("End Date").type(ParameterType.STRING)
			.description("End date for filtering events (ISO 8601).")
			.placeHolder("2024-12-31T23:59:59Z")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("getAll"))))
			.build());

		// Event > GetAll: continuation token
		params.add(NodeParameter.builder()
			.name("eventContinuationToken").displayName("Continuation Token").type(ParameterType.STRING)
			.description("Continuation token for pagination.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("event"), "operation", List.of("getAll"))))
			.build());
	}

	// ========================= Group Parameters =========================

	private void addGroupParameters(List<NodeParameter> params) {
		// Group > Create: name
		params.add(NodeParameter.builder()
			.name("groupName").displayName("Group Name").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		// Group > Create: accessAll
		params.add(NodeParameter.builder()
			.name("groupAccessAll").displayName("Access All").type(ParameterType.BOOLEAN).defaultValue(false)
			.description("Whether the group has access to all collections.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		// Group > Create: externalId
		params.add(NodeParameter.builder()
			.name("groupExternalId").displayName("External ID").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		// Group > Create: collections (JSON)
		params.add(NodeParameter.builder()
			.name("groupCollections").displayName("Collections (JSON)").type(ParameterType.STRING)
			.description("JSON array of collection associations, e.g. [{\"id\":\"collId\",\"readOnly\":false}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("create"))))
			.build());

		// Group > Get/Delete/Update/GetMember/UpdateMembers: groupId
		params.add(NodeParameter.builder()
			.name("groupId").displayName("Group ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("get", "delete", "update", "getMember", "updateMembers"))))
			.build());

		// Group > Update: fields
		params.add(NodeParameter.builder()
			.name("groupUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("name").displayName("Name").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("accessAll").displayName("Access All").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("externalId").displayName("External ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("collections").displayName("Collections (JSON)").type(ParameterType.STRING)
					.description("JSON array of collection associations").build()
			)).build());

		// Group > UpdateMembers: memberIds (JSON array)
		params.add(NodeParameter.builder()
			.name("groupMemberIds").displayName("Member IDs (JSON)").type(ParameterType.STRING).required(true)
			.description("JSON array of member IDs, e.g. [\"memberId1\",\"memberId2\"]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("group"), "operation", List.of("updateMembers"))))
			.build());
	}

	// ========================= Member Parameters =========================

	private void addMemberParameters(List<NodeParameter> params) {
		// Member > Create: email
		params.add(NodeParameter.builder()
			.name("memberEmail").displayName("Email").type(ParameterType.STRING).required(true)
			.placeHolder("user@example.com")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create"))))
			.build());

		// Member > Create: type
		params.add(NodeParameter.builder()
			.name("memberType").displayName("Member Type").type(ParameterType.OPTIONS).required(true).defaultValue("2")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Owner").value("0").build(),
				ParameterOption.builder().name("Admin").value("1").build(),
				ParameterOption.builder().name("User").value("2").build(),
				ParameterOption.builder().name("Manager").value("3").build()
			)).build());

		// Member > Create: accessAll
		params.add(NodeParameter.builder()
			.name("memberAccessAll").displayName("Access All").type(ParameterType.BOOLEAN).defaultValue(false)
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create"))))
			.build());

		// Member > Create: externalId
		params.add(NodeParameter.builder()
			.name("memberExternalId").displayName("External ID").type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create"))))
			.build());

		// Member > Create: collections (JSON)
		params.add(NodeParameter.builder()
			.name("memberCollections").displayName("Collections (JSON)").type(ParameterType.STRING)
			.description("JSON array of collection associations, e.g. [{\"id\":\"collId\",\"readOnly\":false}]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("create"))))
			.build());

		// Member > Get/Delete/Update/GetGroups/UpdateGroups: memberId
		params.add(NodeParameter.builder()
			.name("memberId").displayName("Member ID").type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("get", "delete", "update", "getGroups", "updateGroups"))))
			.build());

		// Member > Update: fields
		params.add(NodeParameter.builder()
			.name("memberUpdateFields").displayName("Update Fields")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("update"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("type").displayName("Member Type").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("Owner").value("0").build(),
						ParameterOption.builder().name("Admin").value("1").build(),
						ParameterOption.builder().name("User").value("2").build(),
						ParameterOption.builder().name("Manager").value("3").build()
					)).build(),
				NodeParameter.builder().name("accessAll").displayName("Access All").type(ParameterType.BOOLEAN).build(),
				NodeParameter.builder().name("externalId").displayName("External ID").type(ParameterType.STRING).build(),
				NodeParameter.builder().name("collections").displayName("Collections (JSON)").type(ParameterType.STRING)
					.description("JSON array of collection associations").build()
			)).build());

		// Member > UpdateGroups: groupIds (JSON array)
		params.add(NodeParameter.builder()
			.name("memberGroupIds").displayName("Group IDs (JSON)").type(ParameterType.STRING).required(true)
			.description("JSON array of group IDs, e.g. [\"groupId1\",\"groupId2\"]")
			.displayOptions(Map.of("show", Map.of("resource", List.of("member"), "operation", List.of("updateGroups"))))
			.build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "collection");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String accessToken = getAccessToken(credentials);
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = getAuthHeaders(accessToken);

			return switch (resource) {
				case "collection" -> executeCollection(context, baseUrl, headers);
				case "event" -> executeEvent(context, baseUrl, headers);
				case "group" -> executeGroup(context, baseUrl, headers);
				case "member" -> executeMember(context, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Bitwarden API error: " + e.getMessage(), e);
		}
	}

	// ========================= Collection Execute =========================

	private NodeExecutionResult executeCollection(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String name = context.getParameter("collectionName", "");
				String externalId = context.getParameter("externalId", "");
				String groupsJson = context.getParameter("collectionGroups", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				putIfPresent(body, "externalId", externalId);
				if (!groupsJson.isEmpty()) {
					body.put("groups", objectMapper.readValue(groupsJson, Object.class));
				}

				HttpResponse<String> response = post(baseUrl + "/collections", body, headers);
				return toResult(response);
			}
			case "delete": {
				String collectionId = context.getParameter("collectionId", "");
				HttpResponse<String> response = delete(baseUrl + "/collections/" + encode(collectionId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String collectionId = context.getParameter("collectionId", "");
				HttpResponse<String> response = get(baseUrl + "/collections/" + encode(collectionId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/collections", headers);
				return toListResult(response, "data");
			}
			case "update": {
				String collectionId = context.getParameter("collectionId", "");
				Map<String, Object> updateFields = context.getParameter("collectionUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				putIfPresent(body, "externalId", updateFields.get("externalId"));
				String groupsJson = String.valueOf(updateFields.getOrDefault("groups", ""));
				if (!groupsJson.isEmpty()) {
					body.put("groups", objectMapper.readValue(groupsJson, Object.class));
				}

				HttpResponse<String> response = put(baseUrl + "/collections/" + encode(collectionId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown collection operation: " + operation);
		}
	}

	// ========================= Event Execute =========================

	private NodeExecutionResult executeEvent(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String start = context.getParameter("eventStart", "");
		String end = context.getParameter("eventEnd", "");
		String continuationToken = context.getParameter("eventContinuationToken", "");

		String url = baseUrl + "/events";
		Map<String, Object> queryParams = new LinkedHashMap<>();
		if (!start.isEmpty()) queryParams.put("start", start);
		if (!end.isEmpty()) queryParams.put("end", end);
		if (!continuationToken.isEmpty()) queryParams.put("continuationToken", continuationToken);
		url = buildUrl(url, queryParams);

		HttpResponse<String> response = get(url, headers);
		return toListResult(response, "data");
	}

	// ========================= Group Execute =========================

	private NodeExecutionResult executeGroup(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String name = context.getParameter("groupName", "");
				boolean accessAll = toBoolean(context.getParameter("groupAccessAll", false), false);
				String externalId = context.getParameter("groupExternalId", "");
				String collectionsJson = context.getParameter("groupCollections", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("name", name);
				body.put("accessAll", accessAll);
				putIfPresent(body, "externalId", externalId);
				if (!collectionsJson.isEmpty()) {
					body.put("collections", objectMapper.readValue(collectionsJson, Object.class));
				}

				HttpResponse<String> response = post(baseUrl + "/groups", body, headers);
				return toResult(response);
			}
			case "delete": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = delete(baseUrl + "/groups/" + encode(groupId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(groupId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/groups", headers);
				return toListResult(response, "data");
			}
			case "getMember": {
				String groupId = context.getParameter("groupId", "");
				HttpResponse<String> response = get(baseUrl + "/groups/" + encode(groupId) + "/member-ids", headers);
				String body = response.body();
				if (body == null || body.isBlank()) {
					return NodeExecutionResult.empty();
				}
				List<String> memberIds = objectMapper.readValue(body, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
				List<Map<String, Object>> items = new ArrayList<>();
				for (String memberId : memberIds) {
					items.add(wrapInJson(Map.of("memberId", memberId)));
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}
			case "update": {
				String groupId = context.getParameter("groupId", "");
				Map<String, Object> updateFields = context.getParameter("groupUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				putIfPresent(body, "name", updateFields.get("name"));
				if (updateFields.get("accessAll") != null) {
					body.put("accessAll", toBoolean(updateFields.get("accessAll"), false));
				}
				putIfPresent(body, "externalId", updateFields.get("externalId"));
				String collectionsJson = String.valueOf(updateFields.getOrDefault("collections", ""));
				if (!collectionsJson.isEmpty()) {
					body.put("collections", objectMapper.readValue(collectionsJson, Object.class));
				}

				HttpResponse<String> response = put(baseUrl + "/groups/" + encode(groupId), body, headers);
				return toResult(response);
			}
			case "updateMembers": {
				String groupId = context.getParameter("groupId", "");
				String memberIdsJson = context.getParameter("groupMemberIds", "[]");
				Object memberIds = objectMapper.readValue(memberIdsJson, Object.class);

				HttpResponse<String> response = put(baseUrl + "/groups/" + encode(groupId) + "/member-ids", memberIds, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown group operation: " + operation);
		}
	}

	// ========================= Member Execute =========================

	private NodeExecutionResult executeMember(NodeExecutionContext context, String baseUrl, Map<String, String> headers) throws Exception {
		String operation = context.getParameter("operation", "getAll");

		switch (operation) {
			case "create": {
				String email = context.getParameter("memberEmail", "");
				String type = context.getParameter("memberType", "2");
				boolean accessAll = toBoolean(context.getParameter("memberAccessAll", false), false);
				String externalId = context.getParameter("memberExternalId", "");
				String collectionsJson = context.getParameter("memberCollections", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("email", email);
				body.put("type", Integer.parseInt(type));
				body.put("accessAll", accessAll);
				putIfPresent(body, "externalId", externalId);
				if (!collectionsJson.isEmpty()) {
					body.put("collections", objectMapper.readValue(collectionsJson, Object.class));
				}

				HttpResponse<String> response = post(baseUrl + "/members", body, headers);
				return toResult(response);
			}
			case "delete": {
				String memberId = context.getParameter("memberId", "");
				HttpResponse<String> response = delete(baseUrl + "/members/" + encode(memberId), headers);
				return toDeleteResult(response);
			}
			case "get": {
				String memberId = context.getParameter("memberId", "");
				HttpResponse<String> response = get(baseUrl + "/members/" + encode(memberId), headers);
				return toResult(response);
			}
			case "getAll": {
				HttpResponse<String> response = get(baseUrl + "/members", headers);
				return toListResult(response, "data");
			}
			case "getGroups": {
				String memberId = context.getParameter("memberId", "");
				HttpResponse<String> response = get(baseUrl + "/members/" + encode(memberId) + "/group-ids", headers);
				String body = response.body();
				if (body == null || body.isBlank()) {
					return NodeExecutionResult.empty();
				}
				List<String> groupIds = objectMapper.readValue(body, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
				List<Map<String, Object>> items = new ArrayList<>();
				for (String groupId : groupIds) {
					items.add(wrapInJson(Map.of("groupId", groupId)));
				}
				return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
			}
			case "update": {
				String memberId = context.getParameter("memberId", "");
				Map<String, Object> updateFields = context.getParameter("memberUpdateFields", Map.of());

				Map<String, Object> body = new LinkedHashMap<>();
				if (updateFields.get("type") != null) {
					body.put("type", Integer.parseInt(String.valueOf(updateFields.get("type"))));
				}
				if (updateFields.get("accessAll") != null) {
					body.put("accessAll", toBoolean(updateFields.get("accessAll"), false));
				}
				putIfPresent(body, "externalId", updateFields.get("externalId"));
				String collectionsJson = String.valueOf(updateFields.getOrDefault("collections", ""));
				if (!collectionsJson.isEmpty()) {
					body.put("collections", objectMapper.readValue(collectionsJson, Object.class));
				}

				HttpResponse<String> response = put(baseUrl + "/members/" + encode(memberId), body, headers);
				return toResult(response);
			}
			case "updateGroups": {
				String memberId = context.getParameter("memberId", "");
				String groupIdsJson = context.getParameter("memberGroupIds", "[]");
				Object groupIds = objectMapper.readValue(groupIdsJson, Object.class);

				HttpResponse<String> response = put(baseUrl + "/members/" + encode(memberId) + "/group-ids", groupIds, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown member operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private String getAccessToken(Map<String, Object> credentials) throws Exception {
		String clientId = String.valueOf(credentials.getOrDefault("clientId", ""));
		String clientSecret = String.valueOf(credentials.getOrDefault("clientSecret", ""));
		String url = String.valueOf(credentials.getOrDefault("url", ""));
		String tokenUrl = url + "/identity/connect/token";

		String formBody = "grant_type=client_credentials"
			+ "&client_id=" + encode(clientId)
			+ "&client_secret=" + encode(clientSecret)
			+ "&scope=api.organization";

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/x-www-form-urlencoded");

		HttpResponse<String> response = post(tokenUrl, formBody, headers);
		if (response.statusCode() >= 400) {
			throw new RuntimeException("Failed to obtain Bitwarden access token (HTTP " + response.statusCode() + ")");
		}
		Map<String, Object> tokenResponse = parseResponse(response);
		return String.valueOf(tokenResponse.get("access_token"));
	}

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String url = String.valueOf(credentials.getOrDefault("url", ""));
		return url + "/api";
	}

	private Map<String, String> getAuthHeaders(String accessToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
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
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toListResult(HttpResponse<String> response, String dataKey) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object data = parsed.getOrDefault(dataKey, parsed);
		if (data instanceof List) {
			List<Map<String, Object>> items = new ArrayList<>();
			for (Object item : (List<?>) data) {
				if (item instanceof Map) {
					items.add(wrapInJson(item));
				}
			}
			return items.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(items);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		if (response.body() == null || response.body().isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "statusCode", response.statusCode()))));
		}
		return toResult(response);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Bitwarden API error (HTTP " + response.statusCode() + "): " + body);
	}
}
