package io.trellis.nodes.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * JWT Node — creates, decodes, and verifies JSON Web Tokens.
 */
@Node(
		type = "jwt",
		displayName = "JWT",
		description = "Create, decode and verify JSON Web Tokens",
		category = "Data Transformation",
		icon = "key",
		credentials = {"jwtAuth"}
)
public class JwtNode extends AbstractNode {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String operation = context.getParameter("operation", "sign");
		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (operation) {
					case "sign" -> handleSign(context);
					case "decode" -> handleDecode(context);
					case "verify" -> handleVerify(context);
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

		return NodeExecutionResult.success(results);
	}

	private Map<String, Object> handleSign(NodeExecutionContext context) throws Exception {
		String secret = context.getCredentialString("secret", "");
		String algorithm = context.getParameter("algorithm", "HS256");
		String payloadJson = context.getParameter("payload", "{}");
		int expiresIn = toInt(context.getParameters().get("expiresIn"), 3600);
		String issuer = context.getParameter("issuer", "");
		String subject = context.getParameter("subject", "");
		String audience = context.getParameter("audience", "");

		Map<String, Object> payload = MAPPER.readValue(payloadJson, new TypeReference<>() {});

		long now = Instant.now().getEpochSecond();
		payload.put("iat", now);
		if (expiresIn > 0) {
			payload.put("exp", now + expiresIn);
		}
		if (!issuer.isBlank()) payload.put("iss", issuer);
		if (!subject.isBlank()) payload.put("sub", subject);
		if (!audience.isBlank()) payload.put("aud", audience);

		Map<String, Object> header = new LinkedHashMap<>();
		header.put("alg", algorithm);
		header.put("typ", "JWT");

		String headerEncoded = base64UrlEncode(MAPPER.writeValueAsBytes(header));
		String payloadEncoded = base64UrlEncode(MAPPER.writeValueAsBytes(payload));
		String signingInput = headerEncoded + "." + payloadEncoded;

		String signature;
		if (algorithm.startsWith("HS")) {
			String hmacAlg = "HmacSHA" + algorithm.substring(2);
			Mac mac = Mac.getInstance(hmacAlg);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), hmacAlg));
			signature = base64UrlEncode(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
		} else {
			throw new UnsupportedOperationException("Algorithm " + algorithm + " not yet supported. Use HS256/HS384/HS512.");
		}

		String token = signingInput + "." + signature;

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("token", token);
		return result;
	}

	private Map<String, Object> handleDecode(NodeExecutionContext context) throws Exception {
		String token = context.getParameter("token", "");

		String[] parts = token.split("\\.");
		if (parts.length < 2) {
			throw new IllegalArgumentException("Invalid JWT token format");
		}

		String headerJson = new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
		String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);

		Map<String, Object> header = MAPPER.readValue(headerJson, new TypeReference<>() {});
		Map<String, Object> payload = MAPPER.readValue(payloadJson, new TypeReference<>() {});

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("header", header);
		result.put("payload", payload);
		return result;
	}

	private Map<String, Object> handleVerify(NodeExecutionContext context) throws Exception {
		String token = context.getParameter("token", "");
		String secret = context.getCredentialString("secret", "");

		String[] parts = token.split("\\.");
		if (parts.length != 3) {
			throw new IllegalArgumentException("Invalid JWT token format");
		}

		String headerJson = new String(base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
		String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);

		Map<String, Object> header = MAPPER.readValue(headerJson, new TypeReference<>() {});
		Map<String, Object> payload = MAPPER.readValue(payloadJson, new TypeReference<>() {});

		String algorithm = (String) header.getOrDefault("alg", "HS256");
		String signingInput = parts[0] + "." + parts[1];

		if (algorithm.startsWith("HS")) {
			String hmacAlg = "HmacSHA" + algorithm.substring(2);
			Mac mac = Mac.getInstance(hmacAlg);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), hmacAlg));
			byte[] expectedSig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
			byte[] actualSig = base64UrlDecode(parts[2]);

			if (!MessageDigest.isEqual(expectedSig, actualSig)) {
				throw new SecurityException("JWT signature verification failed");
			}
		} else {
			throw new UnsupportedOperationException("Verification for algorithm " + algorithm + " not yet supported.");
		}

		// Check expiration
		boolean ignoreExpiration = toBoolean(context.getParameters().get("ignoreExpiration"), false);
		if (!ignoreExpiration && payload.containsKey("exp")) {
			long exp = ((Number) payload.get("exp")).longValue();
			if (Instant.now().getEpochSecond() > exp) {
				throw new SecurityException("JWT token has expired");
			}
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("header", header);
		result.put("payload", payload);
		result.put("verified", true);
		return result;
	}

	private String base64UrlEncode(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private byte[] base64UrlDecode(String data) {
		return Base64.getUrlDecoder().decode(data);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS)
						.defaultValue("sign")
						.options(List.of(
								ParameterOption.builder().name("Sign").value("sign").build(),
								ParameterOption.builder().name("Decode").value("decode").build(),
								ParameterOption.builder().name("Verify").value("verify").build()
						)).build(),
				NodeParameter.builder()
						.name("algorithm").displayName("Algorithm")
						.type(ParameterType.OPTIONS)
						.defaultValue("HS256")
						.options(List.of(
								ParameterOption.builder().name("HS256").value("HS256").build(),
								ParameterOption.builder().name("HS384").value("HS384").build(),
								ParameterOption.builder().name("HS512").value("HS512").build()
						)).build(),
				NodeParameter.builder()
						.name("payload").displayName("Payload (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JWT payload claims as JSON.").build(),
				NodeParameter.builder()
						.name("expiresIn").displayName("Expires In (seconds)")
						.type(ParameterType.NUMBER).defaultValue(3600)
						.description("Token lifetime in seconds. 0 for no expiration.").build(),
				NodeParameter.builder()
						.name("issuer").displayName("Issuer")
						.type(ParameterType.STRING).defaultValue("")
						.description("Issuer claim (iss).").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("")
						.description("Subject claim (sub).").build(),
				NodeParameter.builder()
						.name("audience").displayName("Audience")
						.type(ParameterType.STRING).defaultValue("")
						.description("Audience claim (aud).").build(),
				NodeParameter.builder()
						.name("token").displayName("Token")
						.type(ParameterType.STRING).defaultValue("")
						.description("JWT token string to decode or verify.").build(),
				NodeParameter.builder()
						.name("ignoreExpiration").displayName("Ignore Expiration")
						.type(ParameterType.BOOLEAN).defaultValue(false)
						.description("Skip expiration validation during verify.").build()
		);
	}
}
