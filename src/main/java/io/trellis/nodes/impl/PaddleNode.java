package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Paddle — manage products, coupons, plans, payments, and subscriptions
 * using the Paddle API. All requests are POST with form-based authentication.
 */
@Node(
		type = "paddle",
		displayName = "Paddle",
		description = "Manage products and subscriptions in Paddle",
		category = "E-Commerce / Payments",
		icon = "paddle",
		credentials = {"paddleApi"}
)
public class PaddleNode extends AbstractApiNode {

	private static final String PRODUCTION_BASE_URL = "https://vendors.paddle.com/api";
	private static final String SANDBOX_BASE_URL = "https://sandbox-vendors.paddle.com/api";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String vendorId = (String) credentials.getOrDefault("vendorId", "");
		String vendorAuthCode = (String) credentials.getOrDefault("vendorAuthCode", "");
		boolean useSandbox = toBoolean(credentials.get("sandbox"), false);

		String baseUrl = useSandbox ? SANDBOX_BASE_URL : PRODUCTION_BASE_URL;

		String resource = context.getParameter("resource", "product");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/x-www-form-urlencoded");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "coupon" -> handleCoupon(context, baseUrl, headers, vendorId, vendorAuthCode, operation);
					case "payment" -> handlePayment(context, baseUrl, headers, vendorId, vendorAuthCode, operation);
					case "plan" -> handlePlan(context, baseUrl, headers, vendorId, vendorAuthCode, operation);
					case "product" -> handleProduct(context, baseUrl, headers, vendorId, vendorAuthCode, operation);
					case "user" -> handleUser(context, baseUrl, headers, vendorId, vendorAuthCode, operation);
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

	private Map<String, Object> handleCoupon(NodeExecutionContext context, String baseUrl, Map<String, String> headers,
			String vendorId, String vendorAuthCode, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String couponType = context.getParameter("couponType", "product");
				params.put("coupon_type", couponType);
				String discountType = context.getParameter("discountType", "flat");
				params.put("discount_type", discountType);
				String discountAmount = context.getParameter("discountAmount", "");
				if (!discountAmount.isEmpty()) params.put("discount_amount", discountAmount);
				String couponCode = context.getParameter("couponCode", "");
				if (!couponCode.isEmpty()) params.put("coupon_code", couponCode);
				String productIds = context.getParameter("productIds", "");
				if (!productIds.isEmpty()) params.put("product_ids", productIds);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				HttpResponse<String> response = post(baseUrl + "/2.1/product/create_coupon", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String productId = context.getParameter("productId", "");
				if (!productId.isEmpty()) params.put("product_id", productId);
				HttpResponse<String> response = post(baseUrl + "/2.1/product/list_coupons", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String couponCode = context.getParameter("couponCode", "");
				if (!couponCode.isEmpty()) params.put("coupon_code", couponCode);
				String newCouponCode = context.getParameter("newCouponCode", "");
				if (!newCouponCode.isEmpty()) params.put("new_coupon_code", newCouponCode);
				String discountAmount = context.getParameter("discountAmount", "");
				if (!discountAmount.isEmpty()) params.put("discount_amount", discountAmount);
				String group = context.getParameter("group", "");
				if (!group.isEmpty()) params.put("group", group);
				HttpResponse<String> response = post(baseUrl + "/2.1/product/update_coupon", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown coupon operation: " + operation);
		};
	}

	private Map<String, Object> handlePayment(NodeExecutionContext context, String baseUrl, Map<String, String> headers,
			String vendorId, String vendorAuthCode, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String subscriptionId = context.getParameter("subscriptionId", "");
				if (!subscriptionId.isEmpty()) params.put("subscription_id", subscriptionId);
				HttpResponse<String> response = post(baseUrl + "/2.0/subscription/payments", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "reschedule" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String paymentId = context.getParameter("paymentId", "");
				if (!paymentId.isEmpty()) params.put("payment_id", paymentId);
				String date = context.getParameter("date", "");
				if (!date.isEmpty()) params.put("date", date);
				HttpResponse<String> response = post(baseUrl + "/2.0/subscription/payments_reschedule", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payment operation: " + operation);
		};
	}

	private Map<String, Object> handlePlan(NodeExecutionContext context, String baseUrl, Map<String, String> headers,
			String vendorId, String vendorAuthCode, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String planId = context.getParameter("planId", "");
				if (!planId.isEmpty()) params.put("plan", planId);
				HttpResponse<String> response = post(baseUrl + "/2.0/subscription/plans", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				HttpResponse<String> response = post(baseUrl + "/2.0/subscription/plans", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown plan operation: " + operation);
		};
	}

	private Map<String, Object> handleProduct(NodeExecutionContext context, String baseUrl, Map<String, String> headers,
			String vendorId, String vendorAuthCode, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				HttpResponse<String> response = post(baseUrl + "/2.0/product/get_products", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown product operation: " + operation);
		};
	}

	private Map<String, Object> handleUser(NodeExecutionContext context, String baseUrl, Map<String, String> headers,
			String vendorId, String vendorAuthCode, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				params.put("vendor_id", vendorId);
				params.put("vendor_auth_code", vendorAuthCode);
				String subscriptionId = context.getParameter("subscriptionId", "");
				if (!subscriptionId.isEmpty()) params.put("subscription_id", subscriptionId);
				String planId = context.getParameter("planId", "");
				if (!planId.isEmpty()) params.put("plan_id", planId);
				String state = context.getParameter("state", "");
				if (!state.isEmpty()) params.put("state", state);
				HttpResponse<String> response = post(baseUrl + "/2.0/subscription/users", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown user operation: " + operation);
		};
	}

	private String buildFormBody(Map<String, Object> params) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			if (!sb.isEmpty()) sb.append("&");
			sb.append(encode(entry.getKey()))
				.append("=")
				.append(encode(String.valueOf(entry.getValue())));
		}
		return sb.toString();
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("product")
						.options(List.of(
								ParameterOption.builder().name("Coupon").value("coupon").build(),
								ParameterOption.builder().name("Payment").value("payment").build(),
								ParameterOption.builder().name("Plan").value("plan").build(),
								ParameterOption.builder().name("Product").value("product").build(),
								ParameterOption.builder().name("User").value("user").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Reschedule").value("reschedule").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("couponType").displayName("Coupon Type")
						.type(ParameterType.OPTIONS).defaultValue("product")
						.options(List.of(
								ParameterOption.builder().name("Checkout").value("checkout").build(),
								ParameterOption.builder().name("Product").value("product").build()
						)).build(),
				NodeParameter.builder()
						.name("discountType").displayName("Discount Type")
						.type(ParameterType.OPTIONS).defaultValue("flat")
						.options(List.of(
								ParameterOption.builder().name("Flat").value("flat").build(),
								ParameterOption.builder().name("Percentage").value("percentage").build()
						)).build(),
				NodeParameter.builder()
						.name("discountAmount").displayName("Discount Amount")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("couponCode").displayName("Coupon Code")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("newCouponCode").displayName("New Coupon Code")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productId").displayName("Product ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productIds").displayName("Product IDs")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of product IDs.").build(),
				NodeParameter.builder()
						.name("planId").displayName("Plan ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subscriptionId").displayName("Subscription ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentId").displayName("Payment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("date").displayName("Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("New payment date (YYYY-MM-DD format).").build(),
				NodeParameter.builder()
						.name("state").displayName("State")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Active").value("active").build(),
								ParameterOption.builder().name("Past Due").value("past_due").build(),
								ParameterOption.builder().name("Trialling").value("trialling").build(),
								ParameterOption.builder().name("Paused").value("paused").build()
						)).build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("group").displayName("Group")
						.type(ParameterType.STRING).defaultValue("")
						.description("Coupon group name.").build()
		);
	}
}
