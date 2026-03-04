package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Mindee — extract data from receipts and invoices using Mindee's OCR API.
 */
@Node(
		type = "mindee",
		displayName = "Mindee",
		description = "Extract data from receipts and invoices with Mindee OCR",
		category = "Miscellaneous",
		icon = "mindee",
		credentials = {"mindeeReceiptApi", "mindeeInvoiceApi"},
		searchOnly = true
)
public class MindeeNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.mindee.net/v1/products/mindee";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "receipt");
		String apiKey;
		if ("receipt".equals(resource)) {
			apiKey = context.getCredentialString("apiKey", "");
		} else {
			apiKey = context.getCredentialString("apiKey", "");
		}

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Token " + apiKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "receipt" -> {
						String documentUrl = context.getParameter("documentUrl", "");
						Map<String, Object> body = Map.of("document", documentUrl);
						headers.put("Content-Type", "application/json");
						HttpResponse<String> response = post(BASE_URL + "/expense_receipts/v4/predict", body, headers);
						yield parseResponse(response);
					}
					case "invoice" -> {
						String documentUrl = context.getParameter("documentUrl", "");
						Map<String, Object> body = Map.of("document", documentUrl);
						headers.put("Content-Type", "application/json");
						HttpResponse<String> response = post(BASE_URL + "/invoices/v4/predict", body, headers);
						yield parseResponse(response);
					}
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

		return NodeExecutionResult.success(results);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("receipt")
						.options(List.of(
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Receipt").value("receipt").build()
						)).build(),
				NodeParameter.builder()
						.name("documentUrl").displayName("Document URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("URL of the document to process.").build()
		);
	}
}
