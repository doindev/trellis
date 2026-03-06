package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * WooCommerce — manage customers, orders, products, and coupons
 * using the WooCommerce REST API (v3).
 */
@Node(
		type = "wooCommerce",
		displayName = "WooCommerce",
		description = "Manage customers, orders, products, and coupons in WooCommerce",
		category = "E-Commerce / Payments",
		icon = "wooCommerce",
		credentials = {"wooCommerceApi"}
)
public class WooCommerceNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String consumerKey = (String) credentials.getOrDefault("consumerKey", "");
		String consumerSecret = (String) credentials.getOrDefault("consumerSecret", "");
		String storeUrl = (String) credentials.getOrDefault("url", "");

		// Remove trailing slash if present
		if (storeUrl.endsWith("/")) {
			storeUrl = storeUrl.substring(0, storeUrl.length() - 1);
		}

		String baseUrl = storeUrl + "/wp-json/wc/v3";

		String resource = context.getParameter("resource", "order");
		String operation = context.getParameter("operation", "getAll");

		// WooCommerce uses Basic Auth with consumer key and secret
		String basicAuth = Base64.getEncoder().encodeToString((consumerKey + ":" + consumerSecret).getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + basicAuth);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "customer" -> handleCustomer(context, baseUrl, headers, operation);
					case "order" -> handleOrder(context, baseUrl, headers, operation);
					case "product" -> handleProduct(context, baseUrl, headers, operation);
					case "coupon" -> handleCoupon(context, baseUrl, headers, operation);
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

	// ---- Customer ----

	private Map<String, Object> handleCustomer(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				String username = context.getParameter("username", "");
				if (!username.isEmpty()) body.put("username", username);
				HttpResponse<String> response = post(baseUrl + "/customers", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(baseUrl + "/customers/" + encode(customerId) + "?force=true", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = get(baseUrl + "/customers/" + encode(customerId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/customers?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String customerId = context.getParameter("customerId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				HttpResponse<String> response = put(baseUrl + "/customers/" + encode(customerId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	// ---- Order ----

	private Map<String, Object> handleOrder(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String paymentMethod = context.getParameter("paymentMethod", "");
				if (!paymentMethod.isEmpty()) body.put("payment_method", paymentMethod);
				String paymentMethodTitle = context.getParameter("paymentMethodTitle", "");
				if (!paymentMethodTitle.isEmpty()) body.put("payment_method_title", paymentMethodTitle);
				String status = context.getParameter("status", "pending");
				body.put("status", status);
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) body.put("customer_id", Integer.parseInt(customerId));
				String lineItemsJson = context.getParameter("lineItems", "[]");
				body.put("line_items", parseJsonArray(lineItemsJson));
				HttpResponse<String> response = post(baseUrl + "/orders", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = delete(baseUrl + "/orders/" + encode(orderId) + "?force=true", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = get(baseUrl + "/orders/" + encode(orderId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/orders?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String orderId = context.getParameter("orderId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String status = context.getParameter("status", "");
				if (!status.isEmpty()) body.put("status", status);
				String note = context.getParameter("note", "");
				if (!note.isEmpty()) body.put("customer_note", note);
				HttpResponse<String> response = put(baseUrl + "/orders/" + encode(orderId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown order operation: " + operation);
		};
	}

	// ---- Product ----

	private Map<String, Object> handleProduct(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String type = context.getParameter("productType", "simple");
				body.put("type", type);
				String regularPrice = context.getParameter("regularPrice", "");
				if (!regularPrice.isEmpty()) body.put("regular_price", regularPrice);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String shortDescription = context.getParameter("shortDescription", "");
				if (!shortDescription.isEmpty()) body.put("short_description", shortDescription);
				String sku = context.getParameter("sku", "");
				if (!sku.isEmpty()) body.put("sku", sku);
				HttpResponse<String> response = post(baseUrl + "/products", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String productId = context.getParameter("productId", "");
				HttpResponse<String> response = delete(baseUrl + "/products/" + encode(productId) + "?force=true", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String productId = context.getParameter("productId", "");
				HttpResponse<String> response = get(baseUrl + "/products/" + encode(productId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/products?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String productId = context.getParameter("productId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) body.put("name", name);
				String regularPrice = context.getParameter("regularPrice", "");
				if (!regularPrice.isEmpty()) body.put("regular_price", regularPrice);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String sku = context.getParameter("sku", "");
				if (!sku.isEmpty()) body.put("sku", sku);
				HttpResponse<String> response = put(baseUrl + "/products/" + encode(productId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown product operation: " + operation);
		};
	}

	// ---- Coupon ----

	private Map<String, Object> handleCoupon(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String code = context.getParameter("couponCode", "");
				if (!code.isEmpty()) body.put("code", code);
				String discountType = context.getParameter("discountType", "percent");
				body.put("discount_type", discountType);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) body.put("amount", amount);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				HttpResponse<String> response = post(baseUrl + "/coupons", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String couponId = context.getParameter("couponId", "");
				HttpResponse<String> response = delete(baseUrl + "/coupons/" + encode(couponId) + "?force=true", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String couponId = context.getParameter("couponId", "");
				HttpResponse<String> response = get(baseUrl + "/coupons/" + encode(couponId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(baseUrl + "/coupons?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String couponId = context.getParameter("couponId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) body.put("amount", amount);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				HttpResponse<String> response = put(baseUrl + "/coupons/" + encode(couponId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown coupon operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("order")
						.options(List.of(
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("Order").value("order").build(),
								ParameterOption.builder().name("Product").value("product").build(),
								ParameterOption.builder().name("Coupon").value("coupon").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("customerId").displayName("Customer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("orderId").displayName("Order ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productId").displayName("Product ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("couponId").displayName("Coupon ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("username").displayName("Username")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("shortDescription").displayName("Short Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("regularPrice").displayName("Regular Price")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sku").displayName("SKU")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productType").displayName("Product Type")
						.type(ParameterType.OPTIONS).defaultValue("simple")
						.options(List.of(
								ParameterOption.builder().name("Simple").value("simple").build(),
								ParameterOption.builder().name("Grouped").value("grouped").build(),
								ParameterOption.builder().name("External").value("external").build(),
								ParameterOption.builder().name("Variable").value("variable").build()
						)).build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.OPTIONS).defaultValue("pending")
						.options(List.of(
								ParameterOption.builder().name("Pending").value("pending").build(),
								ParameterOption.builder().name("Processing").value("processing").build(),
								ParameterOption.builder().name("On Hold").value("on-hold").build(),
								ParameterOption.builder().name("Completed").value("completed").build(),
								ParameterOption.builder().name("Cancelled").value("cancelled").build(),
								ParameterOption.builder().name("Refunded").value("refunded").build(),
								ParameterOption.builder().name("Failed").value("failed").build()
						)).build(),
				NodeParameter.builder()
						.name("paymentMethod").displayName("Payment Method")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentMethodTitle").displayName("Payment Method Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("note").displayName("Note")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lineItems").displayName("Line Items (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of line items for order creation.").build(),
				NodeParameter.builder()
						.name("couponCode").displayName("Coupon Code")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("discountType").displayName("Discount Type")
						.type(ParameterType.OPTIONS).defaultValue("percent")
						.options(List.of(
								ParameterOption.builder().name("Percentage").value("percent").build(),
								ParameterOption.builder().name("Fixed Cart").value("fixed_cart").build(),
								ParameterOption.builder().name("Fixed Product").value("fixed_product").build()
						)).build(),
				NodeParameter.builder()
						.name("amount").displayName("Amount")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Max results to return.").build()
		);
	}
}
