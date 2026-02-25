package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Simple API — various utility endpoints (website info, PDF generation,
 * QR codes, social media profiles, etc.) via the OneSimpleAPI.
 */
@Node(
		type = "oneSimpleApi",
		displayName = "One Simple API",
		description = "Access various utility endpoints via One Simple API",
		category = "Miscellaneous",
		icon = "cog",
		credentials = {"oneSimpleApi"}
)
public class OneSimpleApiNode extends AbstractApiNode {

	private static final String BASE_URL = "https://onesimpleapi.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiToken = context.getCredentialString("apiToken");
			String resource = context.getParameter("resource", "websiteInfo");
			String url = context.getParameter("url", "");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					Map<String, Object> qs = new LinkedHashMap<>();
					qs.put("token", apiToken);

					String endpoint;
					switch (resource) {
						case "websiteInfo" -> {
							endpoint = "/website_info";
							qs.put("url", url);
						}
						case "screenshot" -> {
							endpoint = "/screenshot";
							qs.put("url", url);
						}
						case "pdf" -> {
							endpoint = "/pdf";
							qs.put("url", url);
						}
						case "qrCode" -> {
							endpoint = "/qr_code";
							String message = context.getParameter("message", "");
							qs.put("message", message);
						}
						case "socialProfile" -> {
							endpoint = "/social_profile";
							qs.put("url", url);
						}
						case "exchangeRate" -> {
							endpoint = "/exchange_rate";
							String fromCurrency = context.getParameter("fromCurrency", "USD");
							String toCurrency = context.getParameter("toCurrency", "EUR");
							qs.put("from", fromCurrency);
							qs.put("to", toCurrency);
						}
						default -> endpoint = "/website_info";
					}

					String requestUrl = buildUrl(BASE_URL + endpoint, qs);
					var response = get(requestUrl, Map.of());
					results.add(wrapInJson(parseResponse(response)));
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

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("websiteInfo")
						.options(List.of(
								ParameterOption.builder().name("Website Info").value("websiteInfo").build(),
								ParameterOption.builder().name("Screenshot").value("screenshot").build(),
								ParameterOption.builder().name("PDF").value("pdf").build(),
								ParameterOption.builder().name("QR Code").value("qrCode").build(),
								ParameterOption.builder().name("Social Profile").value("socialProfile").build(),
								ParameterOption.builder().name("Exchange Rate").value("exchangeRate").build()
						)).build(),
				NodeParameter.builder()
						.name("url").displayName("URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("Target URL for website info, screenshot, PDF, or social profile.").build(),
				NodeParameter.builder()
						.name("message").displayName("Message")
						.type(ParameterType.STRING).defaultValue("")
						.description("Content for QR code generation.").build(),
				NodeParameter.builder()
						.name("fromCurrency").displayName("From Currency")
						.type(ParameterType.STRING).defaultValue("USD").build(),
				NodeParameter.builder()
						.name("toCurrency").displayName("To Currency")
						.type(ParameterType.STRING).defaultValue("EUR").build()
		);
	}
}
