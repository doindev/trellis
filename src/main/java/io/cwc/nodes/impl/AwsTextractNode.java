package io.cwc.nodes.impl;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.*;

import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * AWS Textract — extract text, forms, and tables from documents and images.
 */
@Node(
		type = "awsTextract",
		displayName = "AWS Textract",
		description = "Extract text and data from documents and images",
		category = "AWS",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsTextractNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String operation = context.getParameter("operation", "detectText");

		TextractClient client = TextractClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "detectText" -> handleDetectText(context, client);
					case "analyzeDocument" -> handleAnalyzeDocument(context, client);
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

	private Map<String, Object> handleDetectText(NodeExecutionContext context, TextractClient client) {
		Document document = buildDocument(context);
		boolean simplify = toBoolean(context.getParameters().get("simplify"), true);

		DetectDocumentTextResponse response = client.detectDocumentText(
				DetectDocumentTextRequest.builder().document(document).build());

		if (simplify) {
			StringBuilder fullText = new StringBuilder();
			for (Block block : response.blocks()) {
				if (block.blockType() == BlockType.LINE) {
					fullText.append(block.text()).append("\n");
				}
			}
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("text", fullText.toString().trim());
			return result;
		}

		return blocksToMap(response.blocks());
	}

	private Map<String, Object> handleAnalyzeDocument(NodeExecutionContext context, TextractClient client) {
		Document document = buildDocument(context);
		String featureTypes = context.getParameter("featureTypes", "TABLES,FORMS");

		List<FeatureType> features = Arrays.stream(featureTypes.split(","))
				.map(String::trim)
				.map(FeatureType::fromValue)
				.toList();

		AnalyzeDocumentResponse response = client.analyzeDocument(
				AnalyzeDocumentRequest.builder()
						.document(document)
						.featureTypes(features)
						.build());

		return blocksToMap(response.blocks());
	}

	@SuppressWarnings("unchecked")
	private Document buildDocument(NodeExecutionContext context) {
		boolean binaryData = toBoolean(context.getParameters().get("binaryData"), false);

		if (binaryData) {
			String binaryPropertyName = context.getParameter("binaryPropertyName", "data");
			Map<String, Object> firstItem = context.getInputData().get(0);
			Map<String, Object> binary = (Map<String, Object>) firstItem.get("binary");
			if (binary == null) {
				binary = (Map<String, Object>) unwrapJson(firstItem).get("binary");
			}
			if (binary == null) {
				throw new IllegalStateException("No binary data found");
			}
			Map<String, Object> fileData = (Map<String, Object>) binary.get(binaryPropertyName);
			if (fileData == null) {
				throw new IllegalStateException("No binary data in field: " + binaryPropertyName);
			}
			byte[] data = Base64.getDecoder().decode((String) fileData.get("data"));
			return Document.builder()
					.bytes(SdkBytes.fromByteArray(data))
					.build();
		} else {
			String bucket = context.getParameter("bucket", "");
			String name = context.getParameter("name", "");
			String version = context.getParameter("version", "");

			S3Object.Builder s3Builder = S3Object.builder()
					.bucket(bucket)
					.name(name);
			if (!version.isBlank()) {
				s3Builder.version(version);
			}
			return Document.builder()
					.s3Object(s3Builder.build())
					.build();
		}
	}

	private Map<String, Object> blocksToMap(List<Block> blocks) {
		List<Map<String, Object>> blockList = new ArrayList<>();
		for (Block block : blocks) {
			Map<String, Object> b = new LinkedHashMap<>();
			b.put("blockType", block.blockTypeAsString());
			b.put("id", block.id());
			b.put("text", block.text());
			b.put("confidence", block.confidence());
			b.put("page", block.page());
			if (block.geometry() != null && block.geometry().boundingBox() != null) {
				BoundingBox box = block.geometry().boundingBox();
				b.put("boundingBox", Map.of(
						"width", box.width(), "height", box.height(),
						"left", box.left(), "top", box.top()));
			}
			blockList.add(b);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("blocks", blockList);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("detectText")
						.options(List.of(
								ParameterOption.builder().name("Detect Document Text").value("detectText").build(),
								ParameterOption.builder().name("Analyze Document").value("analyzeDocument").build()
						)).build(),
				NodeParameter.builder()
						.name("binaryData").displayName("Binary Data")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Use binary data input instead of S3 reference.").build(),
				NodeParameter.builder()
						.name("binaryPropertyName").displayName("Binary Property Name")
						.type(ParameterType.STRING).defaultValue("data")
						.description("The binary property containing document data.").build(),
				NodeParameter.builder()
						.name("bucket").displayName("S3 Bucket")
						.type(ParameterType.STRING).defaultValue("")
						.description("The S3 bucket containing the document.").build(),
				NodeParameter.builder()
						.name("name").displayName("S3 Object Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The S3 object key (file name).").build(),
				NodeParameter.builder()
						.name("version").displayName("S3 Object Version")
						.type(ParameterType.STRING).defaultValue("")
						.description("Optional S3 object version.").build(),
				NodeParameter.builder()
						.name("featureTypes").displayName("Feature Types")
						.type(ParameterType.STRING).defaultValue("TABLES,FORMS")
						.description("Comma-separated feature types for analysis (TABLES, FORMS).").build(),
				NodeParameter.builder()
						.name("simplify").displayName("Simplify")
						.type(ParameterType.BOOLEAN).defaultValue(true)
						.description("Return only extracted text instead of full block data.").build()
		);
	}
}
