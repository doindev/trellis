package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * PayPal — manage payments, orders, and payouts using the PayPal REST API.
 * Uses OAuth2 client credentials flow for authentication.
 */
@Node(
		type = "payPal",
		displayName = "PayPal",
		description = "Manage payments, orders, and payouts in PayPal",
		category = "E-Commerce / Payments",
		icon = "payPal",
		credentials = {"payPalApi"}
)
public class PayPalNode extends AbstractApiNode {

	private static final String PRODUCTION_BASE_URL = "https://api-m.paypal.com";
	private static final String SANDBOX_BASE_URL = "https://api-m.sandbox.paypal.com";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String clientId = (String) credentials.getOrDefault("clientId", "");
		String clientSecret = (String) credentials.getOrDefault("clientSecret", "");
		boolean useSandbox = toBoolean(credentials.get("sandbox"), true);

		String envBaseUrl = useSandbox ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;

		String resource = context.getParameter("resource", "payment");
		String operation = context.getParameter("operation", "getAll");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				String accessToken = getAccessToken(envBaseUrl, clientId, clientSecret);

				Map<String, String> headers = new HashMap<>();
				headers.put("Authorization", "Bearer " + accessToken);
				headers.put("Content-Type", "application/json");
				headers.put("Accept", "application/json");

				Map<String, Object> result = switch (resource) {
					case "payment" -> handlePayment(context, envBaseUrl, headers, operation);
					case "order" -> handleOrder(context, envBaseUrl, headers, operation);
					case "payout" -> handlePayout(context, envBaseUrl, headers, operation);
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

	private String getAccessToken(String baseUrl, String clientId, String clientSecret) throws Exception {
		String basicAuth = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + basicAuth);
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		headers.put("Accept", "application/json");

		HttpResponse<String> response = post(baseUrl + "/v1/oauth2/token", "grant_type=client_credentials", headers);
		Map<String, Object> tokenResponse = parseResponse(response);
		return (String) tokenResponse.getOrDefault("access_token", "");
	}

	// ---- Payment ----

	private Map<String, Object> handlePayment(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("intent", context.getParameter("intent", "sale"));
				Map<String, Object> payer = new LinkedHashMap<>();
				payer.put("payment_method", context.getParameter("paymentMethod", "paypal"));
				body.put("payer", payer);

				String transactionsJson = context.getParameter("transactions", "[]");
				body.put("transactions", parseJsonArray(transactionsJson));

				String redirectUrlsJson = context.getParameter("redirectUrls", "{}");
				Map<String, Object> redirectUrls = parseJson(redirectUrlsJson);
				if (!redirectUrls.isEmpty()) body.put("redirect_urls", redirectUrls);

				HttpResponse<String> response = post(baseUrl + "/v1/payments/payment", body, headers);
				yield parseResponse(response);
			}
			case "execute" -> {
				String paymentId = context.getParameter("paymentId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String payerId = context.getParameter("payerId", "");
				body.put("payer_id", payerId);
				HttpResponse<String> response = post(baseUrl + "/v1/payments/payment/" + encode(paymentId) + "/execute", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> response = get(baseUrl + "/v1/payments/payment/" + encode(paymentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int count = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/v1/payments/payment?count=" + count, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payment operation: " + operation);
		};
	}

	// ---- Order ----

	private Map<String, Object> handleOrder(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("intent", context.getParameter("orderIntent", "CAPTURE"));
				String purchaseUnitsJson = context.getParameter("purchaseUnits", "[]");
				body.put("purchase_units", parseJsonArray(purchaseUnitsJson));
				HttpResponse<String> response = post(baseUrl + "/v2/checkout/orders", body, headers);
				yield parseResponse(response);
			}
			case "capture" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = post(baseUrl + "/v2/checkout/orders/" + encode(orderId) + "/capture", Map.of(), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = get(baseUrl + "/v2/checkout/orders/" + encode(orderId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown order operation: " + operation);
		};
	}

	// ---- Payout ----

	private Map<String, Object> handlePayout(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				Map<String, Object> senderBatchHeader = new LinkedHashMap<>();
				String emailSubject = context.getParameter("emailSubject", "");
				if (!emailSubject.isEmpty()) senderBatchHeader.put("email_subject", emailSubject);
				String emailMessage = context.getParameter("emailMessage", "");
				if (!emailMessage.isEmpty()) senderBatchHeader.put("email_message", emailMessage);
				body.put("sender_batch_header", senderBatchHeader);

				String payoutItemsJson = context.getParameter("payoutItems", "[]");
				body.put("items", parseJsonArray(payoutItemsJson));

				HttpResponse<String> response = post(baseUrl + "/v1/payments/payouts", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String payoutBatchId = context.getParameter("payoutBatchId", "");
				HttpResponse<String> response = get(baseUrl + "/v1/payments/payouts/" + encode(payoutBatchId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/v1/payments/payouts?page_size=" + limit, headers);
				yield parseResponse(response);
			}
			case "getItem" -> {
				String payoutItemId = context.getParameter("payoutItemId", "");
				HttpResponse<String> response = get(baseUrl + "/v1/payments/payouts-item/" + encode(payoutItemId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payout operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("payment")
						.options(List.of(
								ParameterOption.builder().name("Payment").value("payment").build(),
								ParameterOption.builder().name("Order").value("order").build(),
								ParameterOption.builder().name("Payout").value("payout").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Capture").value("capture").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Execute").value("execute").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Get Item").value("getItem").build()
						)).build(),
				NodeParameter.builder()
						.name("paymentId").displayName("Payment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("payerId").displayName("Payer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("orderId").displayName("Order ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("payoutBatchId").displayName("Payout Batch ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("payoutItemId").displayName("Payout Item ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("intent").displayName("Payment Intent")
						.type(ParameterType.OPTIONS).defaultValue("sale")
						.options(List.of(
								ParameterOption.builder().name("Sale").value("sale").build(),
								ParameterOption.builder().name("Authorize").value("authorize").build(),
								ParameterOption.builder().name("Order").value("order").build()
						)).build(),
				NodeParameter.builder()
						.name("orderIntent").displayName("Order Intent")
						.type(ParameterType.OPTIONS).defaultValue("CAPTURE")
						.options(List.of(
								ParameterOption.builder().name("Capture").value("CAPTURE").build(),
								ParameterOption.builder().name("Authorize").value("AUTHORIZE").build()
						)).build(),
				NodeParameter.builder()
						.name("paymentMethod").displayName("Payment Method")
						.type(ParameterType.OPTIONS).defaultValue("paypal")
						.options(List.of(
								ParameterOption.builder().name("PayPal").value("paypal").build(),
								ParameterOption.builder().name("Credit Card").value("credit_card").build()
						)).build(),
				NodeParameter.builder()
						.name("transactions").displayName("Transactions (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of transaction objects.").build(),
				NodeParameter.builder()
						.name("purchaseUnits").displayName("Purchase Units (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of purchase unit objects.").build(),
				NodeParameter.builder()
						.name("redirectUrls").displayName("Redirect URLs (JSON)")
						.type(ParameterType.JSON).defaultValue("{}")
						.description("JSON object with return_url and cancel_url.").build(),
				NodeParameter.builder()
						.name("emailSubject").displayName("Email Subject")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("emailMessage").displayName("Email Message")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("payoutItems").displayName("Payout Items (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of payout item objects.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Max results to return.").build()
		);
	}
}
