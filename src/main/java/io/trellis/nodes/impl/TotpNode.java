package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TOTP — generates Time-based One-Time Passwords (RFC 6238).
 * Mirrors n8n's totp node. Supports HMAC-SHA1, HMAC-SHA256, and HMAC-SHA512
 * algorithms with configurable digits and period.
 */
@Node(
		type = "totp",
		displayName = "TOTP",
		description = "Generate time-based one-time passwords (RFC 6238)",
		category = "Data Transformation",
		icon = "key"
)
public class TotpNode extends AbstractNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			String secret = context.getParameter("secret", "");
			String algorithm = context.getParameter("algorithm", "SHA1");
			int digits = toInt(context.getParameters().get("digits"), 6);
			int period = toInt(context.getParameters().get("period"), 30);

			if (secret.isBlank()) {
				return handleError(context, "Secret is required", new IllegalArgumentException("Secret is required"));
			}

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					long timeStep = System.currentTimeMillis() / 1000 / period;
					String otp = generateTOTP(secret, timeStep, digits, algorithm);

					Map<String, Object> result = Map.of(
							"otp", otp
					);
					results.add(wrapInJson(result));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}

			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	private String generateTOTP(String secret, long timeStep, int digits, String algorithm) throws Exception {
		byte[] key = base32Decode(secret.replaceAll("\\s", "").toUpperCase());
		byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeStep).array();

		String hmacAlgorithm = switch (algorithm.toUpperCase()) {
			case "SHA256" -> "HmacSHA256";
			case "SHA512" -> "HmacSHA512";
			default -> "HmacSHA1";
		};

		Mac mac = Mac.getInstance(hmacAlgorithm);
		mac.init(new SecretKeySpec(key, hmacAlgorithm));
		byte[] hash = mac.doFinal(timeBytes);

		int offset = hash[hash.length - 1] & 0x0F;
		int binary = ((hash[offset] & 0x7F) << 24)
				| ((hash[offset + 1] & 0xFF) << 16)
				| ((hash[offset + 2] & 0xFF) << 8)
				| (hash[offset + 3] & 0xFF);

		int otp = binary % (int) Math.pow(10, digits);
		return String.format("%0" + digits + "d", otp);
	}

	private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private byte[] base32Decode(String input) {
		input = input.replaceAll("=", "");
		int totalBits = input.length() * 5;
		byte[] result = new byte[totalBits / 8];
		int buffer = 0;
		int bitsLeft = 0;
		int index = 0;

		for (char c : input.toCharArray()) {
			int val = BASE32_CHARS.indexOf(c);
			if (val < 0) throw new IllegalArgumentException("Invalid Base32 character: " + c);
			buffer = (buffer << 5) | val;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				bitsLeft -= 8;
				result[index++] = (byte) (buffer >> bitsLeft);
				buffer &= (1 << bitsLeft) - 1;
			}
		}

		return result;
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("secret").displayName("Secret")
						.type(ParameterType.STRING)
						.defaultValue("")
						.required(true)
						.description("Base32-encoded secret key.").build(),
				NodeParameter.builder()
						.name("algorithm").displayName("Algorithm")
						.type(ParameterType.OPTIONS)
						.defaultValue("SHA1")
						.options(List.of(
								ParameterOption.builder().name("SHA1").value("SHA1").build(),
								ParameterOption.builder().name("SHA256").value("SHA256").build(),
								ParameterOption.builder().name("SHA512").value("SHA512").build()
						)).build(),
				NodeParameter.builder()
						.name("digits").displayName("Digits")
						.type(ParameterType.NUMBER)
						.defaultValue(6)
						.description("Number of digits in the OTP (typically 6 or 8).").build(),
				NodeParameter.builder()
						.name("period").displayName("Period")
						.type(ParameterType.NUMBER)
						.defaultValue(30)
						.description("Time step in seconds (typically 30).").build()
		);
	}
}
