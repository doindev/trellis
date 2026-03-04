package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Unleashed Software — query sales orders and stock on hand using the Unleashed API.
 */
@Node(
		type = "unleashedSoftware",
		displayName = "Unleashed Software",
		description = "Query sales orders and stock data from Unleashed",
		category = "Miscellaneous",
		icon = "unleashedSoftware",
		credentials = {"unleashedSoftwareApi"},
		searchOnly = true
)
public class UnleashedSoftwareNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.unleashedsoftware.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String apiId = context.getCredentialString("apiId", "");
		String apiKey = context.getCredentialString("apiKey", "");

		String resource = context.getParameter("resource", "stockOnHand");
		String operation = context.getParameter("operation", "getAll");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "salesOrder" -> handleSalesOrder(context, apiId, apiKey, operation);
					case "stockOnHand" -> handleStockOnHand(context, apiId, apiKey, operation);
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

	private Map<String, Object> handleSalesOrder(NodeExecutionContext context, String apiId, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				int page = toInt(context.getParameters().get("page"), 1);
				StringBuilder qs = new StringBuilder();
				String startDate = context.getParameter("startDate", "");
				if (!startDate.isEmpty()) qs.append("startDate=").append(encode(startDate)).append("&");
				String endDate = context.getParameter("endDate", "");
				if (!endDate.isEmpty()) qs.append("endDate=").append(encode(endDate)).append("&");
				String orderStatus = context.getParameter("orderStatus", "");
				if (!orderStatus.isEmpty()) qs.append("orderStatus=").append(encode(orderStatus)).append("&");
				int pageSize = toInt(context.getParameters().get("pageSize"), 200);
				qs.append("pageSize=").append(pageSize);

				String queryString = qs.toString();
				String signature = hmacSha256(queryString, apiKey);
				Map<String, String> headers = buildHeaders(apiId, signature);
				HttpResponse<String> response = get(BASE_URL + "/SalesOrders/" + page + "?" + queryString, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown sales order operation: " + operation);
		};
	}

	private Map<String, Object> handleStockOnHand(NodeExecutionContext context, String apiId, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String productId = context.getParameter("productId", "");
				String queryString = "";
				String signature = hmacSha256(queryString, apiKey);
				Map<String, String> headers = buildHeaders(apiId, signature);
				HttpResponse<String> response = get(BASE_URL + "/StockOnHand/" + encode(productId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int page = toInt(context.getParameters().get("page"), 1);
				StringBuilder qs = new StringBuilder();
				int pageSize = toInt(context.getParameters().get("pageSize"), 200);
				qs.append("pageSize=").append(pageSize);
				String modifiedSince = context.getParameter("modifiedSince", "");
				if (!modifiedSince.isEmpty()) qs.append("&modifiedSince=").append(encode(modifiedSince));

				String queryString = qs.toString();
				String signature = hmacSha256(queryString, apiKey);
				Map<String, String> headers = buildHeaders(apiId, signature);
				HttpResponse<String> response = get(BASE_URL + "/StockOnHand/" + page + "?" + queryString, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown stock on hand operation: " + operation);
		};
	}

	private Map<String, String> buildHeaders(String apiId, String signature) {
		Map<String, String> headers = new HashMap<>();
		headers.put("api-auth-id", apiId);
		headers.put("api-auth-signature", signature);
		headers.put("Accept", "application/json");
		headers.put("Content-Type", "application/json");
		return headers;
	}

	private String hmacSha256(String data, String key) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(), "HmacSHA256");
		mac.init(secretKey);
		byte[] hash = mac.doFinal(data.getBytes());
		return Base64.getEncoder().encodeToString(hash);
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("stockOnHand")
						.options(List.of(
								ParameterOption.builder().name("Sales Order").value("salesOrder").build(),
								ParameterOption.builder().name("Stock On Hand").value("stockOnHand").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("productId").displayName("Product ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("GUID of the product.").build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter start date.").build(),
				NodeParameter.builder()
						.name("endDate").displayName("End Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter end date.").build(),
				NodeParameter.builder()
						.name("orderStatus").displayName("Order Status")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter by order status.").build(),
				NodeParameter.builder()
						.name("modifiedSince").displayName("Modified Since")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter by modification date.").build(),
				NodeParameter.builder()
						.name("page").displayName("Page")
						.type(ParameterType.NUMBER).defaultValue(1)
						.description("Page number.").build(),
				NodeParameter.builder()
						.name("pageSize").displayName("Page Size")
						.type(ParameterType.NUMBER).defaultValue(200)
						.description("Results per page.").build()
		);
	}
}
