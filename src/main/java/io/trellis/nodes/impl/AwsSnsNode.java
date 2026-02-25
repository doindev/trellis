package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import java.util.*;

/**
 * AWS SNS — create topics, delete topics, and publish messages to SNS.
 */
@Node(
		type = "awsSns",
		displayName = "AWS SNS",
		description = "Create, delete and publish to AWS SNS topics",
		category = "AWS Services",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsSnsNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "publish");

		SnsClient client = SnsClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "create" -> handleCreate(context, client);
					case "delete" -> handleDelete(context, client);
					case "publish" -> handlePublish(context, client);
					default -> throw new IllegalArgumentException("Unknown operation: " + operation);
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

	private Map<String, Object> handleCreate(NodeExecutionContext context, SnsClient client) {
		String name = context.getParameter("name", "");
		boolean fifoTopic = toBoolean(context.getParameters().get("fifoTopic"), false);
		String displayName = context.getParameter("displayName", "");

		CreateTopicRequest.Builder builder = CreateTopicRequest.builder().name(name);

		Map<String, String> attributes = new HashMap<>();
		if (fifoTopic) {
			attributes.put("FifoTopic", "true");
		}
		if (!displayName.isBlank()) {
			attributes.put("DisplayName", displayName);
		}
		if (!attributes.isEmpty()) {
			builder.attributes(attributes);
		}

		CreateTopicResponse response = client.createTopic(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("topicArn", response.topicArn());
		return result;
	}

	private Map<String, Object> handleDelete(NodeExecutionContext context, SnsClient client) {
		String topicArn = context.getParameter("topicArn", "");

		client.deleteTopic(DeleteTopicRequest.builder().topicArn(topicArn).build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("topicArn", topicArn);
		return result;
	}

	private Map<String, Object> handlePublish(NodeExecutionContext context, SnsClient client) {
		String topicArn = context.getParameter("topicArn", "");
		String subject = context.getParameter("subject", "");
		String message = context.getParameter("message", "");

		PublishRequest.Builder builder = PublishRequest.builder()
				.topicArn(topicArn)
				.message(message);

		if (!subject.isBlank()) {
			builder.subject(subject);
		}

		PublishResponse response = client.publish(builder.build());

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("messageId", response.messageId());
		result.put("topicArn", topicArn);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("publish")
						.options(List.of(
								ParameterOption.builder().name("Create Topic").value("create").build(),
								ParameterOption.builder().name("Delete Topic").value("delete").build(),
								ParameterOption.builder().name("Publish").value("publish").build()
						)).build(),
				NodeParameter.builder()
						.name("topicArn").displayName("Topic ARN")
						.type(ParameterType.STRING).defaultValue("")
						.description("The ARN of the SNS topic.").build(),
				NodeParameter.builder()
						.name("name").displayName("Topic Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Name for the new topic (create operation).").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Subject line for email endpoints.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message to publish.").build(),
				NodeParameter.builder()
						.name("fifoTopic").displayName("FIFO Topic")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Enable FIFO ordering for the topic.").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("Display name for SMS subscriptions.").build()
		);
	}
}
