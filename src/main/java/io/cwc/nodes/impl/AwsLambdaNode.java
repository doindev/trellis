package io.cwc.nodes.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import software.amazon.awssdk.services.lambda.model.InvocationType;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * AWS Lambda — invokes Lambda functions.
 */
@Node(
		type = "awsLambda",
		displayName = "AWS Lambda",
		description = "Invoke AWS Lambda functions",
		category = "AWS",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsLambdaNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");

		String functionName = context.getParameter("function", "");
		String qualifier = context.getParameter("qualifier", "$LATEST");
		String invocationTypeStr = context.getParameter("invocationType", "RequestResponse");
		String payload = context.getParameter("payload", "");

		LambdaClient client = LambdaClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		try {
			InvokeRequest.Builder requestBuilder = InvokeRequest.builder()
					.functionName(functionName)
					.qualifier(qualifier)
					.invocationType(InvocationType.fromValue(invocationTypeStr));

			if (!payload.isBlank()) {
				requestBuilder.payload(SdkBytes.fromString(payload, StandardCharsets.UTF_8));
			}

			InvokeResponse response = client.invoke(requestBuilder.build());

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("statusCode", response.statusCode());
			result.put("functionError", response.functionError());

			if (response.payload() != null) {
				String responsePayload = response.payload().asUtf8String();
				try {
					Object parsed = MAPPER.readValue(responsePayload, Object.class);
					result.put("result", parsed);
				} catch (Exception e) {
					result.put("result", responsePayload);
				}
			}

			return NodeExecutionResult.success(List.of(wrapInJson(result)));
		} catch (Exception e) {
			if (context.isContinueOnFail()) {
				return handleError(context, e.getMessage(), e);
			}
			throw new RuntimeException(e);
		} finally {
			client.close();
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("function").displayName("Function")
						.type(ParameterType.STRING).defaultValue("")
						.description("The Lambda function name or ARN to invoke.").build(),
				NodeParameter.builder()
						.name("qualifier").displayName("Qualifier")
						.type(ParameterType.STRING).defaultValue("$LATEST")
						.description("Version or alias to invoke.").build(),
				NodeParameter.builder()
						.name("invocationType").displayName("Invocation Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("RequestResponse")
						.options(List.of(
								ParameterOption.builder().name("Request Response (sync)").value("RequestResponse").build(),
								ParameterOption.builder().name("Event (async)").value("Event").build()
						)).build(),
				NodeParameter.builder()
						.name("payload").displayName("Payload (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("JSON payload provided to the Lambda function as input.").build()
		);
	}
}
