package io.trellis.nodes.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Crypto Node - performs cryptographic operations.
 * Operations: hash, hmac, generate (UUID, password, random bytes).
 */
@Slf4j
@Node(
	type = "crypto",
	displayName = "Crypto",
	description = "Perform cryptographic operations: hashing, HMAC, and random generation.",
	category = "Data Transformation",
	icon = "lock"
)
public class CryptoNode extends AbstractNode {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
		return List.of(
			NodeParameter.builder()
				.name("action")
				.displayName("Action")
				.description("The cryptographic operation to perform.")
				.type(ParameterType.OPTIONS)
				.defaultValue("hash")
				.options(List.of(
					ParameterOption.builder().name("Hash").value("hash")
						.description("Generate a hash of the input value").build(),
					ParameterOption.builder().name("HMAC").value("hmac")
						.description("Generate an HMAC of the input value").build(),
					ParameterOption.builder().name("Generate").value("generate")
						.description("Generate random data (UUID, password, hex)").build()
				))
				.build(),

			// Hash parameters
			NodeParameter.builder()
				.name("hashType")
				.displayName("Algorithm")
				.description("The hash algorithm to use.")
				.type(ParameterType.OPTIONS)
				.defaultValue("SHA-256")
				.displayOptions(Map.of("show", Map.of("action", List.of("hash", "hmac"))))
				.options(List.of(
					ParameterOption.builder().name("MD5").value("MD5").build(),
					ParameterOption.builder().name("SHA-1").value("SHA-1").build(),
					ParameterOption.builder().name("SHA-256").value("SHA-256").build(),
					ParameterOption.builder().name("SHA-384").value("SHA-384").build(),
					ParameterOption.builder().name("SHA-512").value("SHA-512").build()
				))
				.build(),

			NodeParameter.builder()
				.name("value")
				.displayName("Value")
				.description("The value to hash. Use an expression like ={{ $json.fieldName }} to hash a specific field.")
				.type(ParameterType.STRING)
				.required(true)
				.displayOptions(Map.of("show", Map.of("action", List.of("hash", "hmac"))))
				.build(),

			NodeParameter.builder()
				.name("secret")
				.displayName("Secret")
				.description("The secret key for HMAC.")
				.type(ParameterType.STRING)
				.required(true)
				.displayOptions(Map.of("show", Map.of("action", List.of("hmac"))))
				.build(),

			NodeParameter.builder()
				.name("encoding")
				.displayName("Encoding")
				.description("The output encoding for the hash.")
				.type(ParameterType.OPTIONS)
				.defaultValue("hex")
				.displayOptions(Map.of("show", Map.of("action", List.of("hash", "hmac"))))
				.options(List.of(
					ParameterOption.builder().name("Hex").value("hex").build(),
					ParameterOption.builder().name("Base64").value("base64").build()
				))
				.build(),

			NodeParameter.builder()
				.name("propertyName")
				.displayName("Property Name")
				.description("The field name to store the result in.")
				.type(ParameterType.STRING)
				.defaultValue("data")
				.displayOptions(Map.of("show", Map.of("action", List.of("hash", "hmac"))))
				.build(),

			// Generate parameters
			NodeParameter.builder()
				.name("generateType")
				.displayName("Type")
				.description("What to generate.")
				.type(ParameterType.OPTIONS)
				.defaultValue("uuid")
				.displayOptions(Map.of("show", Map.of("action", List.of("generate"))))
				.options(List.of(
					ParameterOption.builder().name("UUID").value("uuid")
						.description("Generate a random UUID v4").build(),
					ParameterOption.builder().name("Password").value("password")
						.description("Generate a random alphanumeric password").build(),
					ParameterOption.builder().name("Random Hex").value("hex")
						.description("Generate random hex bytes").build()
				))
				.build(),

			NodeParameter.builder()
				.name("length")
				.displayName("Length")
				.description("The length of the generated value (characters for password, bytes for hex).")
				.type(ParameterType.NUMBER)
				.defaultValue(32)
				.minValue(1)
				.displayOptions(Map.of("show", Map.of("action", List.of("generate"))))
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData == null || inputData.isEmpty()) {
			inputData = List.of(Map.of("json", Map.of()));
		}

		String action = context.getParameter("action", "hash");

		try {
			switch (action) {
				case "hash": return executeHash(context, inputData);
				case "hmac": return executeHmac(context, inputData);
				case "generate": return executeGenerate(context, inputData);
				default: return NodeExecutionResult.error("Unknown action: " + action);
			}
		} catch (Exception e) {
			return handleError(context, "Crypto node error: " + e.getMessage(), e);
		}
	}

	private NodeExecutionResult executeHash(NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String hashType = context.getParameter("hashType", "SHA-256");
		String value = context.getParameter("value", "");
		String encoding = context.getParameter("encoding", "hex");
		String propertyName = context.getParameter("propertyName", "data");

		MessageDigest digest = MessageDigest.getInstance(hashType);
		byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
		String hashResult = encodeBytes(hashBytes, encoding);

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
			json.put(propertyName, hashResult);
			result.add(wrapInJson(json));
		}

		log.debug("Crypto: hash {} -> {} ({} encoding)", hashType, propertyName, encoding);
		return NodeExecutionResult.success(result);
	}

	private NodeExecutionResult executeHmac(NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String hashType = context.getParameter("hashType", "SHA-256");
		String value = context.getParameter("value", "");
		String secret = context.getParameter("secret", "");
		String encoding = context.getParameter("encoding", "hex");
		String propertyName = context.getParameter("propertyName", "data");

		String macAlgorithm = "Hmac" + hashType.replace("-", "");
		Mac mac = Mac.getInstance(macAlgorithm);
		SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), macAlgorithm);
		mac.init(secretKey);
		byte[] hmacBytes = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		String hmacResult = encodeBytes(hmacBytes, encoding);

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
			json.put(propertyName, hmacResult);
			result.add(wrapInJson(json));
		}

		log.debug("Crypto: HMAC {} -> {} ({} encoding)", hashType, propertyName, encoding);
		return NodeExecutionResult.success(result);
	}

	private NodeExecutionResult executeGenerate(NodeExecutionContext context,
			List<Map<String, Object>> inputData) throws Exception {
		String generateType = context.getParameter("generateType", "uuid");
		int length = Math.max(1, toInt(context.getParameter("length", 32), 32));

		String generated;
		switch (generateType) {
			case "uuid":
				generated = UUID.randomUUID().toString();
				break;
			case "password":
				generated = generatePassword(length);
				break;
			case "hex":
				byte[] bytes = new byte[length];
				SECURE_RANDOM.nextBytes(bytes);
				generated = bytesToHex(bytes);
				break;
			default:
				generated = UUID.randomUUID().toString();
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> item : inputData) {
			Map<String, Object> json = new LinkedHashMap<>(unwrapJson(item));
			json.put("data", generated);
			result.add(wrapInJson(json));
		}

		log.debug("Crypto: generate {} (length={})", generateType, length);
		return NodeExecutionResult.success(result);
	}

	private String encodeBytes(byte[] bytes, String encoding) {
		if ("base64".equals(encoding)) {
			return Base64.getEncoder().encodeToString(bytes);
		}
		return bytesToHex(bytes);
	}

	private String bytesToHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}

	private String generatePassword(int length) {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
		}
		return sb.toString();
	}
}
