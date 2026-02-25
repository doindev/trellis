package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.util.*;

/**
 * AWS Rekognition — analyze images for faces, labels, text, moderation content, and celebrities.
 */
@Node(
		type = "awsRekognition",
		displayName = "AWS Rekognition",
		description = "Analyze images for faces, labels, text, and celebrities",
		category = "AWS Services",
		icon = "aws",
		credentials = {"aws"}
)
public class AwsRekognitionNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String accessKeyId = context.getCredentialString("accessKeyId");
		String secretAccessKey = context.getCredentialString("secretAccessKey");
		String region = context.getCredentialString("region", "us-east-1");
		String type = context.getParameter("type", "detectLabels");

		RekognitionClient client = RekognitionClient.builder()
				.region(Region.of(region))
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
				.build();

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Image image = buildImage(context);

				Map<String, Object> result = switch (type) {
					case "detectFaces" -> detectFaces(context, client, image);
					case "detectLabels" -> detectLabels(context, client, image);
					case "detectModerationLabels" -> detectModerationLabels(context, client, image);
					case "detectText" -> detectText(client, image);
					case "recognizeCelebrity" -> recognizeCelebrities(client, image);
					default -> throw new IllegalArgumentException("Unknown type: " + type);
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

	@SuppressWarnings("unchecked")
	private Image buildImage(NodeExecutionContext context) {
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
			return Image.builder().bytes(SdkBytes.fromByteArray(data)).build();
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
			return Image.builder().s3Object(s3Builder.build()).build();
		}
	}

	private Map<String, Object> detectFaces(NodeExecutionContext context,
			RekognitionClient client, Image image) {
		String attributes = context.getParameter("attributes", "DEFAULT");

		DetectFacesRequest request = DetectFacesRequest.builder()
				.image(image)
				.attributes(Attribute.fromValue(attributes))
				.build();

		DetectFacesResponse response = client.detectFaces(request);

		List<Map<String, Object>> faces = new ArrayList<>();
		for (FaceDetail face : response.faceDetails()) {
			Map<String, Object> f = new LinkedHashMap<>();
			f.put("confidence", face.confidence());
			f.put("ageRange", face.ageRange() != null
					? Map.of("low", face.ageRange().low(), "high", face.ageRange().high()) : null);
			f.put("gender", face.gender() != null
					? Map.of("value", face.gender().valueAsString(), "confidence", face.gender().confidence()) : null);
			f.put("smile", face.smile() != null
					? Map.of("value", face.smile().value(), "confidence", face.smile().confidence()) : null);
			if (face.boundingBox() != null) {
				f.put("boundingBox", Map.of(
						"width", face.boundingBox().width(),
						"height", face.boundingBox().height(),
						"left", face.boundingBox().left(),
						"top", face.boundingBox().top()));
			}
			faces.add(f);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("faces", faces);
		return result;
	}

	private Map<String, Object> detectLabels(NodeExecutionContext context,
			RekognitionClient client, Image image) {
		int maxLabels = toInt(context.getParameters().get("maxLabels"), 0);
		float minConfidence = (float) toDouble(context.getParameters().get("minConfidence"), 0);

		DetectLabelsRequest.Builder builder = DetectLabelsRequest.builder().image(image);
		if (maxLabels > 0) builder.maxLabels(maxLabels);
		if (minConfidence > 0) builder.minConfidence(minConfidence);

		DetectLabelsResponse response = client.detectLabels(builder.build());

		List<Map<String, Object>> labels = new ArrayList<>();
		for (Label label : response.labels()) {
			Map<String, Object> l = new LinkedHashMap<>();
			l.put("name", label.name());
			l.put("confidence", label.confidence());
			List<String> parents = new ArrayList<>();
			for (Parent p : label.parents()) {
				parents.add(p.name());
			}
			l.put("parents", parents);
			labels.add(l);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("labels", labels);
		return result;
	}

	private Map<String, Object> detectModerationLabels(NodeExecutionContext context,
			RekognitionClient client, Image image) {
		float minConfidence = (float) toDouble(context.getParameters().get("minConfidence"), 0);

		DetectModerationLabelsRequest.Builder builder = DetectModerationLabelsRequest.builder().image(image);
		if (minConfidence > 0) builder.minConfidence(minConfidence);

		DetectModerationLabelsResponse response = client.detectModerationLabels(builder.build());

		List<Map<String, Object>> labels = new ArrayList<>();
		for (ModerationLabel label : response.moderationLabels()) {
			Map<String, Object> l = new LinkedHashMap<>();
			l.put("name", label.name());
			l.put("confidence", label.confidence());
			l.put("parentName", label.parentName());
			labels.add(l);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("moderationLabels", labels);
		return result;
	}

	private Map<String, Object> detectText(RekognitionClient client, Image image) {
		DetectTextResponse response = client.detectText(
				DetectTextRequest.builder().image(image).build());

		List<Map<String, Object>> textDetections = new ArrayList<>();
		for (TextDetection td : response.textDetections()) {
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("detectedText", td.detectedText());
			t.put("type", td.typeAsString());
			t.put("confidence", td.confidence());
			t.put("id", td.id());
			t.put("parentId", td.parentId());
			textDetections.add(t);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("textDetections", textDetections);
		return result;
	}

	private Map<String, Object> recognizeCelebrities(RekognitionClient client, Image image) {
		RecognizeCelebritiesResponse response = client.recognizeCelebrities(
				RecognizeCelebritiesRequest.builder().image(image).build());

		List<Map<String, Object>> celebrities = new ArrayList<>();
		for (Celebrity celeb : response.celebrityFaces()) {
			Map<String, Object> c = new LinkedHashMap<>();
			c.put("name", celeb.name());
			c.put("id", celeb.id());
			c.put("matchConfidence", celeb.matchConfidence());
			c.put("urls", celeb.urls());
			celebrities.add(c);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("celebrities", celebrities);
		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("type").displayName("Analysis Type")
						.type(ParameterType.OPTIONS)
						.defaultValue("detectLabels")
						.options(List.of(
								ParameterOption.builder().name("Detect Faces").value("detectFaces").build(),
								ParameterOption.builder().name("Detect Labels").value("detectLabels").build(),
								ParameterOption.builder().name("Detect Moderation Labels").value("detectModerationLabels").build(),
								ParameterOption.builder().name("Detect Text").value("detectText").build(),
								ParameterOption.builder().name("Recognize Celebrities").value("recognizeCelebrity").build()
						)).build(),
				NodeParameter.builder()
						.name("binaryData").displayName("Binary Data")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Use binary data input instead of S3 reference.").build(),
				NodeParameter.builder()
						.name("binaryPropertyName").displayName("Binary Property Name")
						.type(ParameterType.STRING).defaultValue("data")
						.description("The binary property containing image data.").build(),
				NodeParameter.builder()
						.name("bucket").displayName("S3 Bucket")
						.type(ParameterType.STRING).defaultValue("")
						.description("The S3 bucket containing the image.").build(),
				NodeParameter.builder()
						.name("name").displayName("S3 Object Key")
						.type(ParameterType.STRING).defaultValue("")
						.description("The S3 object key (file name).").build(),
				NodeParameter.builder()
						.name("version").displayName("S3 Object Version")
						.type(ParameterType.STRING).defaultValue("")
						.description("Optional S3 object version.").build(),
				NodeParameter.builder()
						.name("attributes").displayName("Attributes")
						.type(ParameterType.OPTIONS)
						.defaultValue("DEFAULT")
						.options(List.of(
								ParameterOption.builder().name("Default").value("DEFAULT").build(),
								ParameterOption.builder().name("All").value("ALL").build()
						))
						.description("Level of detail for face attributes (detectFaces only).").build(),
				NodeParameter.builder()
						.name("maxLabels").displayName("Max Labels")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Maximum number of labels to return (0 for all).").build(),
				NodeParameter.builder()
						.name("minConfidence").displayName("Min Confidence")
						.type(ParameterType.NUMBER).defaultValue(0)
						.description("Minimum confidence threshold (0-100).").build()
		);
	}
}
