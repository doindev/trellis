package io.trellis.nodes.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;

/**
 * AWS SQS — sends messages to SQS queues.
 */
@Node(
		type = "awsSqs",
		displayName = "AWS SQS",
		description = "Send messages to AWS SQS queues",
		category = "AWS Services",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsSqsNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");

		String queueUrl = context.getParameter("queueUrl", "");
		String queueType = context.getParameter("queueType", "standard");
		boolean sendInputData = toBoolean(context.getParameters().get("sendInputData"), true);
		String messageBody = context.getParameter("messageBody", "");
		String messageGroupId = context.getParameter("messageGroupId", "");
		int delaySeconds = toInt(context.getParameters().get("delaySeconds"), 0);
		String messageDeduplicationId = context.getParameter("messageDeduplicationId", "");

		SqsClient client = SqsClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (Map<String, Object> item : inputData) {
			try {
				String body;
				if (sendInputData) {
					body = MAPPER.writeValueAsString(unwrapJson(item));
				} else {
					body = messageBody;
				}

				SendMessageRequest.Builder builder = SendMessageRequest.builder()
						.queueUrl(queueUrl)
						.messageBody(body);

				if ("standard".equals(queueType) && delaySeconds > 0) {
					builder.delaySeconds(delaySeconds);
				}

				if ("fifo".equals(queueType)) {
					if (!messageGroupId.isBlank()) {
						builder.messageGroupId(messageGroupId);
					}
					if (!messageDeduplicationId.isBlank()) {
						builder.messageDeduplicationId(messageDeduplicationId);
					}
				}

				SendMessageResponse response = client.sendMessage(builder.build());

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("messageId", response.messageId());
				result.put("md5OfMessageBody", response.md5OfMessageBody());
				if (response.sequenceNumber() != null) {
					result.put("sequenceNumber", response.sequenceNumber());
				}
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("queueUrl").displayName("Queue URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("The URL of the SQS queue.").build(),
				NodeParameter.builder()
						.name("queueType").displayName("Queue Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("standard")
						.options(List.of(
								ParameterOption.builder().name("Standard").value("standard").build(),
								ParameterOption.builder().name("FIFO").value("fifo").build()
						)).build(),
				NodeParameter.builder()
						.name("sendInputData").displayName("Send Input Data")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Send the input data as the message body.").build(),
				NodeParameter.builder()
						.name("messageBody").displayName("Message Body")
						.type(ParameterType.STRING).defaultValue("")
						.description("The message body when not sending input data.").build(),
				NodeParameter.builder()
						.name("messageGroupId").displayName("Message Group ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Tag that specifies the message group (FIFO queues only).").build(),
				NodeParameter.builder()
						.name("delaySeconds").displayName("Delay Seconds")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Delay in seconds (0-900, standard queues only).").build(),
				NodeParameter.builder()
						.name("messageDeduplicationId").displayName("Message Deduplication ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Deduplication token (FIFO queues only).").build()
		);
	}
}
