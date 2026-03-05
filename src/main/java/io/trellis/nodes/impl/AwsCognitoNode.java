package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.*;

/**
 * AWS Cognito — manage users, groups and pools in AWS Cognito.
 */
@Node(
		type = "awsCognito",
		displayName = "AWS Cognito",
		description = "Manage users, groups and pools in AWS Cognito",
		category = "AWS",
		icon = "awsCognito",
		credentials = {"awsApi"}
)
public class AwsCognitoNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String resource = context.getParameter("resource", "user");
		String operation = context.getParameter("operation", "get");

		CognitoIdentityProviderClient client = CognitoIdentityProviderClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "user" -> handleUser(context, client, operation);
					case "group" -> handleGroup(context, client, operation);
					case "userPool" -> handleUserPool(context, client, operation);
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

	private Map<String, Object> handleUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client, String operation) {
		return switch (operation) {
			case "create" -> createUser(context, client);
			case "delete" -> deleteUser(context, client);
			case "get" -> getUser(context, client);
			case "getAll" -> getAllUsers(context, client);
			case "update" -> updateUser(context, client);
			case "enable" -> enableUser(context, client);
			case "disable" -> disableUser(context, client);
			case "confirmSignUp" -> confirmSignUp(context, client);
			case "adminSetUserPassword" -> adminSetUserPassword(context, client);
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private Map<String, Object> createUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");
		String temporaryPassword = context.getParameter("temporaryPassword", "");
		String email = context.getParameter("email", "");
		String messageAction = context.getParameter("messageAction", "");

		AdminCreateUserRequest.Builder builder = AdminCreateUserRequest.builder()
				.userPoolId(userPoolId)
				.username(username);

		if (!temporaryPassword.isBlank()) {
			builder.temporaryPassword(temporaryPassword);
		}

		List<AttributeType> attributes = new ArrayList<>();
		if (!email.isBlank()) {
			attributes.add(AttributeType.builder().name("email").value(email).build());
		}

		String phoneNumber = context.getParameter("phoneNumber", "");
		if (!phoneNumber.isBlank()) {
			attributes.add(AttributeType.builder().name("phone_number").value(phoneNumber).build());
		}

		String name = context.getParameter("name", "");
		if (!name.isBlank()) {
			attributes.add(AttributeType.builder().name("name").value(name).build());
		}

		if (!attributes.isEmpty()) {
			builder.userAttributes(attributes);
		}

		if (!messageAction.isBlank()) {
			builder.messageAction(MessageActionType.fromValue(messageAction));
		}

		AdminCreateUserResponse response = client.adminCreateUser(builder.build());
		UserType user = response.user();

		return userToMap(user);
	}

	private Map<String, Object> deleteUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");

		client.adminDeleteUser(AdminDeleteUserRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		return result;
	}

	private Map<String, Object> getUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");

		AdminGetUserResponse response = client.adminGetUser(AdminGetUserRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("username", response.username());
		result.put("userStatus", response.userStatusAsString());
		result.put("enabled", response.enabled());
		result.put("userCreateDate", response.userCreateDate() != null ? response.userCreateDate().toString() : null);
		result.put("userLastModifiedDate", response.userLastModifiedDate() != null ? response.userLastModifiedDate().toString() : null);

		Map<String, String> attrs = new LinkedHashMap<>();
		if (response.userAttributes() != null) {
			for (AttributeType attr : response.userAttributes()) {
				attrs.put(attr.name(), attr.value());
			}
		}
		result.put("attributes", attrs);
		return result;
	}

	private Map<String, Object> getAllUsers(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 60);
		String filter = context.getParameter("filter", "");

		ListUsersRequest.Builder builder = ListUsersRequest.builder()
				.userPoolId(userPoolId);

		if (!returnAll) {
			builder.limit(Math.min(limit, 60));
		}

		if (!filter.isBlank()) {
			builder.filter(filter);
		}

		List<Map<String, Object>> users = new ArrayList<>();
		String paginationToken = null;
		do {
			if (paginationToken != null) {
				builder.paginationToken(paginationToken);
			}
			ListUsersResponse response = client.listUsers(builder.build());
			for (UserType user : response.users()) {
				users.add(userToMap(user));
				if (!returnAll && users.size() >= limit) break;
			}
			paginationToken = response.paginationToken();
		} while (returnAll && paginationToken != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("users", users);
		return result;
	}

	private Map<String, Object> updateUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");
		String email = context.getParameter("email", "");
		String phoneNumber = context.getParameter("phoneNumber", "");
		String name = context.getParameter("name", "");

		List<AttributeType> attributes = new ArrayList<>();
		if (!email.isBlank()) {
			attributes.add(AttributeType.builder().name("email").value(email).build());
		}
		if (!phoneNumber.isBlank()) {
			attributes.add(AttributeType.builder().name("phone_number").value(phoneNumber).build());
		}
		if (!name.isBlank()) {
			attributes.add(AttributeType.builder().name("name").value(name).build());
		}

		if (attributes.isEmpty()) {
			throw new IllegalArgumentException("At least one attribute must be provided for update");
		}

		client.adminUpdateUserAttributes(AdminUpdateUserAttributesRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.userAttributes(attributes)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		return result;
	}

	private Map<String, Object> enableUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");

		client.adminEnableUser(AdminEnableUserRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		result.put("enabled", true);
		return result;
	}

	private Map<String, Object> disableUser(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");

		client.adminDisableUser(AdminDisableUserRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		result.put("enabled", false);
		return result;
	}

	private Map<String, Object> confirmSignUp(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");

		client.adminConfirmSignUp(AdminConfirmSignUpRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		result.put("confirmed", true);
		return result;
	}

	private Map<String, Object> adminSetUserPassword(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");
		String password = context.getParameter("password", "");
		boolean permanent = toBoolean(context.getParameters().get("permanent"), true);

		client.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.password(password)
				.permanent(permanent)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		return result;
	}

	// ========================= Group Operations =========================

	private Map<String, Object> handleGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client, String operation) {
		return switch (operation) {
			case "create" -> createGroup(context, client);
			case "delete" -> deleteGroup(context, client);
			case "get" -> getGroup(context, client);
			case "getAll" -> getAllGroups(context, client);
			case "addUser" -> addUserToGroup(context, client);
			case "removeUser" -> removeUserFromGroup(context, client);
			default -> throw new IllegalArgumentException("Unknown group operation: " + operation);
		};
	}

	private Map<String, Object> createGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String groupName = context.getParameter("groupName", "");
		String description = context.getParameter("description", "");
		String roleArn = context.getParameter("roleArn", "");
		int precedence = toInt(context.getParameters().get("precedence"), 0);

		CreateGroupRequest.Builder builder = CreateGroupRequest.builder()
				.userPoolId(userPoolId)
				.groupName(groupName);

		if (!description.isBlank()) {
			builder.description(description);
		}
		if (!roleArn.isBlank()) {
			builder.roleArn(roleArn);
		}
		if (precedence > 0) {
			builder.precedence(precedence);
		}

		CreateGroupResponse response = client.createGroup(builder.build());
		GroupType group = response.group();

		return groupToMap(group);
	}

	private Map<String, Object> deleteGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String groupName = context.getParameter("groupName", "");

		client.deleteGroup(DeleteGroupRequest.builder()
				.userPoolId(userPoolId)
				.groupName(groupName)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("groupName", groupName);
		return result;
	}

	private Map<String, Object> getGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String groupName = context.getParameter("groupName", "");

		GetGroupResponse response = client.getGroup(GetGroupRequest.builder()
				.userPoolId(userPoolId)
				.groupName(groupName)
				.build());

		return groupToMap(response.group());
	}

	private Map<String, Object> getAllGroups(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 60);

		ListGroupsRequest.Builder builder = ListGroupsRequest.builder()
				.userPoolId(userPoolId);

		if (!returnAll) {
			builder.limit(Math.min(limit, 60));
		}

		List<Map<String, Object>> groups = new ArrayList<>();
		String nextToken = null;
		do {
			if (nextToken != null) {
				builder.nextToken(nextToken);
			}
			ListGroupsResponse response = client.listGroups(builder.build());
			for (GroupType group : response.groups()) {
				groups.add(groupToMap(group));
				if (!returnAll && groups.size() >= limit) break;
			}
			nextToken = response.nextToken();
		} while (returnAll && nextToken != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("groups", groups);
		return result;
	}

	private Map<String, Object> addUserToGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");
		String groupName = context.getParameter("groupName", "");

		client.adminAddUserToGroup(AdminAddUserToGroupRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.groupName(groupName)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		result.put("groupName", groupName);
		return result;
	}

	private Map<String, Object> removeUserFromGroup(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");
		String username = context.getParameter("username", "");
		String groupName = context.getParameter("groupName", "");

		client.adminRemoveUserFromGroup(AdminRemoveUserFromGroupRequest.builder()
				.userPoolId(userPoolId)
				.username(username)
				.groupName(groupName)
				.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("username", username);
		result.put("groupName", groupName);
		return result;
	}

	// ========================= User Pool Operations =========================

	private Map<String, Object> handleUserPool(NodeExecutionContext context,
			CognitoIdentityProviderClient client, String operation) {
		return switch (operation) {
			case "get" -> getUserPool(context, client);
			case "getAll" -> getAllUserPools(context, client);
			default -> throw new IllegalArgumentException("Unknown userPool operation: " + operation);
		};
	}

	private Map<String, Object> getUserPool(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		String userPoolId = context.getParameter("userPoolId", "");

		DescribeUserPoolResponse response = client.describeUserPool(
				DescribeUserPoolRequest.builder().userPoolId(userPoolId).build());

		UserPoolType pool = response.userPool();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", pool.id());
		result.put("name", pool.name());
		result.put("status", pool.statusAsString());
		result.put("creationDate", pool.creationDate() != null ? pool.creationDate().toString() : null);
		result.put("lastModifiedDate", pool.lastModifiedDate() != null ? pool.lastModifiedDate().toString() : null);
		result.put("estimatedNumberOfUsers", pool.estimatedNumberOfUsers());
		result.put("mfaConfiguration", pool.mfaConfigurationAsString());
		return result;
	}

	private Map<String, Object> getAllUserPools(NodeExecutionContext context,
			CognitoIdentityProviderClient client) {
		boolean returnAll = toBoolean(context.getParameters().get("returnAll"), false);
		int limit = toInt(context.getParameters().get("limit"), 60);

		ListUserPoolsRequest.Builder builder = ListUserPoolsRequest.builder()
				.maxResults(returnAll ? 60 : Math.min(limit, 60));

		List<Map<String, Object>> pools = new ArrayList<>();
		String nextToken = null;
		do {
			if (nextToken != null) {
				builder.nextToken(nextToken);
			}
			ListUserPoolsResponse response = client.listUserPools(builder.build());
			for (UserPoolDescriptionType pool : response.userPools()) {
				Map<String, Object> p = new LinkedHashMap<>();
				p.put("id", pool.id());
				p.put("name", pool.name());
				p.put("status", pool.statusAsString());
				p.put("creationDate", pool.creationDate() != null ? pool.creationDate().toString() : null);
				p.put("lastModifiedDate", pool.lastModifiedDate() != null ? pool.lastModifiedDate().toString() : null);
				pools.add(p);
				if (!returnAll && pools.size() >= limit) break;
			}
			nextToken = response.nextToken();
		} while (returnAll && nextToken != null);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("userPools", pools);
		return result;
	}

	// ========================= Helpers =========================

	private Map<String, Object> userToMap(UserType user) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("username", user.username());
		result.put("userStatus", user.userStatusAsString());
		result.put("enabled", user.enabled());
		result.put("userCreateDate", user.userCreateDate() != null ? user.userCreateDate().toString() : null);
		result.put("userLastModifiedDate", user.userLastModifiedDate() != null ? user.userLastModifiedDate().toString() : null);

		Map<String, String> attrs = new LinkedHashMap<>();
		if (user.attributes() != null) {
			for (AttributeType attr : user.attributes()) {
				attrs.put(attr.name(), attr.value());
			}
		}
		result.put("attributes", attrs);
		return result;
	}

	private Map<String, Object> groupToMap(GroupType group) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("groupName", group.groupName());
		result.put("userPoolId", group.userPoolId());
		result.put("description", group.description());
		result.put("roleArn", group.roleArn());
		result.put("precedence", group.precedence());
		result.put("creationDate", group.creationDate() != null ? group.creationDate().toString() : null);
		result.put("lastModifiedDate", group.lastModifiedDate() != null ? group.lastModifiedDate().toString() : null);
		return result;
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
								ParameterOption.builder().name("Group").value("group").build(),
								ParameterOption.builder().name("User Pool").value("userPool").build()
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
								ParameterOption.builder().name("Enable").value("enable").build(),
								ParameterOption.builder().name("Disable").value("disable").build(),
								ParameterOption.builder().name("Confirm Sign Up").value("confirmSignUp").build(),
								ParameterOption.builder().name("Set Password").value("adminSetUserPassword").build(),
								ParameterOption.builder().name("Add User to Group").value("addUser").build(),
								ParameterOption.builder().name("Remove User from Group").value("removeUser").build()
						)).build(),
				NodeParameter.builder()
						.name("userPoolId").displayName("User Pool ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ID of the Cognito user pool.").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("")
						.description("The username of the user.").build(),
				NodeParameter.builder()
						.name("groupName").displayName("Group Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the group.").build(),
				NodeParameter.builder()
						.name("temporaryPassword").displayName("Temporary Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("Temporary password for the new user.").build(),
				NodeParameter.builder()
						.name("password").displayName("Password")
						.type(ParameterType.STRING).defaultValue("")
						.description("The new password for the user.").build(),
				NodeParameter.builder()
						.name("permanent").displayName("Permanent")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Whether the password is permanent.").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("The email address of the user.").build(),
				NodeParameter.builder()
						.name("phoneNumber").displayName("Phone Number")
						.type(ParameterType.STRING).defaultValue("")
						.description("The phone number of the user (E.164 format).").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("The name of the user.").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("")
						.description("Description for the group.").build(),
				NodeParameter.builder()
						.name("roleArn").displayName("Role ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The IAM role ARN for the group.").build(),
				NodeParameter.builder()
						.name("precedence").displayName("Precedence")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Group precedence (lower value = higher priority).").build(),
				NodeParameter.builder()
						.name("messageAction").displayName("Message Action")
						.type(ParameterType.OPTIONS)
						.defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Default").value("").build(),
								ParameterOption.builder().name("Resend").value("RESEND").build(),
								ParameterOption.builder().name("Suppress").value("SUPPRESS").build()
						))
						.description("Action for the welcome message.").build(),
				NodeParameter.builder()
						.name("filter").displayName("Filter")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter expression for listing users (e.g. email = \"user@example.com\").").build(),
				NodeParameter.builder()
						.name("returnAll").displayName("Return All")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Whether to return all results.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(60)
						.description("Max number of results to return.").build()
		);
	}
}
