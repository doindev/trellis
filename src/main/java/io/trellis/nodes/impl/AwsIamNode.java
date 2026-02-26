package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;

import java.util.*;

/**
 * AWS IAM — manage users and groups in AWS Identity and Access Management.
 */
@Node(
		type = "awsIam",
		displayName = "AWS IAM",
		description = "Manage users and groups in AWS IAM",
		category = "AWS Services",
		icon = "awsIam",
		credentials = {"awsApi"}
)
public class AwsIamNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");

		// IAM is a global service, but the SDK requires a region — use us-east-1
		IamClient client = IamClient.builder()
				.region(Region.US_EAST_1)
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		String resource = context.getParameter("resource", "user");
		String operation = context.getParameter("operation", "get");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "user" -> handleUser(context, client, operation);
					case "group" -> handleGroup(context, client, operation);
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

		client.close();
		return NodeExecutionResult.success(results);
	}

	// ========================= User Operations =========================

	private Map<String, Object> handleUser(NodeExecutionContext context, IamClient client, String operation) {
		return switch (operation) {
			case "create" -> createUser(context, client);
			case "delete" -> deleteUser(context, client);
			case "get" -> getUser(context, client);
			case "getAll" -> getAllUsers(context, client);
			case "update" -> updateUser(context, client);
			case "addToGroup" -> addUserToGroup(context, client);
			case "removeFromGroup" -> removeUserFromGroup(context, client);
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> createUser(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");
		String path = context.getParameter("path", "");
		String tags = context.getParameter("tags", "");

		CreateUserRequest.Builder builder = CreateUserRequest.builder()
				.userName(userName);

		if (!path.isBlank()) {
			builder.path(path);
		}

		if (!tags.isBlank()) {
			builder.tags(parseTags(tags));
		}

		CreateUserResponse response = client.createUser(builder.build());
		return userToMap(response.user());
	}

	private Map<String, Object> deleteUser(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");

		client.deleteUser(DeleteUserRequest.builder().userName(userName).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("userName", userName);
		return result;
	}

	private Map<String, Object> getUser(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");

		GetUserResponse response = client.getUser(GetUserRequest.builder()
				.userName(userName).build());

		return userToMap(response.user());
	}

	private Map<String, Object> getAllUsers(NodeExecutionContext context, IamClient client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		String pathPrefix = context.getParameter("pathPrefix", "");

		ListUsersRequest.Builder builder = ListUsersRequest.builder();
		if (!returnAll) {
			builder.maxItems(limit);
		}
		if (!pathPrefix.isBlank()) {
			builder.pathPrefix(pathPrefix);
		}

		List<Map<String, Object>> users = new ArrayList<>();
		String marker = null;
		do {
			if (marker != null) {
				builder.marker(marker);
			}
			ListUsersResponse response = client.listUsers(builder.build());
			for (User user : response.users()) {
				users.add(userToMap(user));
				if (!returnAll && users.size() >= limit) break;
			}
			marker = response.isTruncated() ? response.marker() : null;
		} while (returnAll && marker != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("users", users);
		return result;
	}

	private Map<String, Object> updateUser(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");
		String newUserName = context.getParameter("newUserName", "");
		String newPath = context.getParameter("newPath", "");

		UpdateUserRequest.Builder builder = UpdateUserRequest.builder()
				.userName(userName);

		if (!newUserName.isBlank()) {
			builder.newUserName(newUserName);
		}
		if (!newPath.isBlank()) {
			builder.newPath(newPath);
		}

		client.updateUser(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("userName", userName);
		if (!newUserName.isBlank()) {
			result.put("newUserName", newUserName);
		}
		return result;
	}

	private Map<String, Object> addUserToGroup(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");
		String groupName = context.getParameter("groupName", "");

		client.addUserToGroup(AddUserToGroupRequest.builder()
				.userName(userName)
				.groupName(groupName)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("userName", userName);
		result.put("groupName", groupName);
		return result;
	}

	private Map<String, Object> removeUserFromGroup(NodeExecutionContext context, IamClient client) {
		String userName = context.getParameter("userName", "");
		String groupName = context.getParameter("groupName", "");

		client.removeUserFromGroup(RemoveUserFromGroupRequest.builder()
				.userName(userName)
				.groupName(groupName)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("userName", userName);
		result.put("groupName", groupName);
		return result;
	}

	// ========================= Group Operations =========================

	private Map<String, Object> handleGroup(NodeExecutionContext context, IamClient client, String operation) {
		return switch (operation) {
			case "create" -> createGroup(context, client);
			case "delete" -> deleteGroup(context, client);
			case "get" -> getGroup(context, client);
			case "getAll" -> getAllGroups(context, client);
			case "addPolicy" -> addPolicyToGroup(context, client);
			case "removePolicy" -> removePolicyFromGroup(context, client);
			default -> throw new IllegalArgumentException("Unknown group operation: " + operation);
		};
	}

	private Map<String, Object> createGroup(NodeExecutionContext context, IamClient client) {
		String groupName = context.getParameter("groupName", "");
		String path = context.getParameter("path", "");

		CreateGroupRequest.Builder builder = CreateGroupRequest.builder()
				.groupName(groupName);

		if (!path.isBlank()) {
			builder.path(path);
		}

		CreateGroupResponse response = client.createGroup(builder.build());
		return groupToMap(response.group());
	}

	private Map<String, Object> deleteGroup(NodeExecutionContext context, IamClient client) {
		String groupName = context.getParameter("groupName", "");

		client.deleteGroup(DeleteGroupRequest.builder().groupName(groupName).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("groupName", groupName);
		return result;
	}

	private Map<String, Object> getGroup(NodeExecutionContext context, IamClient client) {
		String groupName = context.getParameter("groupName", "");

		GetGroupResponse response = client.getGroup(GetGroupRequest.builder()
				.groupName(groupName).build());

		Map<String, Object> result = groupToMap(response.group());

		// Include group members
		List<Map<String, Object>> members = new ArrayList<>();
		for (User user : response.users()) {
			members.add(userToMap(user));
		}
		result.put("members", members);
		return result;
	}

	private Map<String, Object> getAllGroups(NodeExecutionContext context, IamClient client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 100);
		String pathPrefix = context.getParameter("pathPrefix", "");

		ListGroupsRequest.Builder builder = ListGroupsRequest.builder();
		if (!returnAll) {
			builder.maxItems(limit);
		}
		if (!pathPrefix.isBlank()) {
			builder.pathPrefix(pathPrefix);
		}

		List<Map<String, Object>> groups = new ArrayList<>();
		String marker = null;
		do {
			if (marker != null) {
				builder.marker(marker);
			}
			ListGroupsResponse response = client.listGroups(builder.build());
			for (Group group : response.groups()) {
				groups.add(groupToMap(group));
				if (!returnAll && groups.size() >= limit) break;
			}
			marker = response.isTruncated() ? response.marker() : null;
		} while (returnAll && marker != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("groups", groups);
		return result;
	}

	private Map<String, Object> addPolicyToGroup(NodeExecutionContext context, IamClient client) {
		String groupName = context.getParameter("groupName", "");
		String policyArn = context.getParameter("policyArn", "");

		client.attachGroupPolicy(AttachGroupPolicyRequest.builder()
				.groupName(groupName)
				.policyArn(policyArn)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("groupName", groupName);
		result.put("policyArn", policyArn);
		return result;
	}

	private Map<String, Object> removePolicyFromGroup(NodeExecutionContext context, IamClient client) {
		String groupName = context.getParameter("groupName", "");
		String policyArn = context.getParameter("policyArn", "");

		client.detachGroupPolicy(DetachGroupPolicyRequest.builder()
				.groupName(groupName)
				.policyArn(policyArn)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("groupName", groupName);
		result.put("policyArn", policyArn);
		return result;
	}

	// ========================= Helpers =========================

	private Map<String, Object> userToMap(User user) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("userName", user.userName());
		result.put("userId", user.userId());
		result.put("arn", user.arn());
		result.put("path", user.path());
		result.put("createDate", user.createDate() != null ? user.createDate().toString() : null);
		result.put("passwordLastUsed", user.passwordLastUsed() != null ? user.passwordLastUsed().toString() : null);
		return result;
	}

	private Map<String, Object> groupToMap(Group group) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("groupName", group.groupName());
		result.put("groupId", group.groupId());
		result.put("arn", group.arn());
		result.put("path", group.path());
		result.put("createDate", group.createDate() != null ? group.createDate().toString() : null);
		return result;
	}

	private List<Tag> parseTags(String tagsString) {
		List<Tag> tags = new ArrayList<>();
		for (String pair : tagsString.split(",")) {
			String[] keyValue = pair.trim().split("=", 2);
			if (keyValue.length == 2) {
				tags.add(Tag.builder()
						.key(keyValue[0].trim())
						.value(keyValue[1].trim())
						.build());
			}
		}
		return tags;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS)
						.defaultValue("user")
						.options(List.of(
								ParameterOption.builder().name("User").value("user").build(),
								ParameterOption.builder().name("Group").value("group").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("get")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Add to Group").value("addToGroup").build(),
								ParameterOption.builder().name("Remove from Group").value("removeFromGroup").build(),
								ParameterOption.builder().name("Attach Policy").value("addPolicy").build(),
								ParameterOption.builder().name("Detach Policy").value("removePolicy").build()
						)).build(),
				NodeParameter.builder()
						.name("userName").displayName("User Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the IAM user.").build(),
				NodeParameter.builder()
						.name("newUserName").displayName("New User Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The new name for the IAM user (update operation).").build(),
				NodeParameter.builder()
						.name("groupName").displayName("Group Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the IAM group.").build(),
				NodeParameter.builder()
						.name("policyArn").displayName("Policy ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the IAM policy to attach or detach.").build(),
				NodeParameter.builder()
						.name("path").displayName("Path")
						.type(ParameterType.STRING).defaultValue("")
						.description("The path for the user or group (e.g. /division_abc/).").build(),
				NodeParameter.builder()
						.name("newPath").displayName("New Path")
						.type(ParameterType.STRING).defaultValue("")
						.description("The new path for the user (update operation).").build(),
				NodeParameter.builder()
						.name("pathPrefix").displayName("Path Prefix")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter results by path prefix.").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated key=value pairs (e.g. Department=Engineering,Team=Backend).").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max number of results to return.").build()
		);
	}
}
