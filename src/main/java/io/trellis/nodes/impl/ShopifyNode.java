package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Shopify — manage orders, products, product variants, customers, and collections
 * using the Shopify Admin REST API.
 */
@Node(
		type = "shopify",
		displayName = "Shopify",
		description = "Manage orders, products, customers, and collections in Shopify",
		category = "E-Commerce / Payments",
		icon = "shopify",
		credentials = {"shopifyApi"}
)
public class ShopifyNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String shopName = (String) credentials.getOrDefault("shopName", "");

		String baseUrl = "https://" + shopName + ".myshopify.com/admin/api/2024-01";

		String resource = context.getParameter("resource", "order");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Shopify-Access-Token", accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "order" -> handleOrder(context, baseUrl, headers, operation);
					case "product" -> handleProduct(context, baseUrl, headers, operation);
					case "productVariant" -> handleProductVariant(context, baseUrl, headers, operation);
					case "customer" -> handleCustomer(context, baseUrl, headers, operation);
					case "collection" -> handleCollection(context, baseUrl, headers, operation);
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

	// ---- Order ----

	private Map<String, Object> handleOrder(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> order = new LinkedHashMap<>();
				String lineItemsJson = context.getParameter("lineItems", "[]");
				order.put("line_items", parseJsonArray(lineItemsJson));
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) order.put("email", email);
				String financialStatus = context.getParameter("financialStatus", "");
				if (!financialStatus.isEmpty()) order.put("financial_status", financialStatus);
				String fulfillmentStatus = context.getParameter("fulfillmentStatus", "");
				if (!fulfillmentStatus.isEmpty()) order.put("fulfillment_status", fulfillmentStatus);
				Map<String, Object> body = Map.of("order", order);
				HttpResponse<String> response = post(baseUrl + "/orders.json", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = delete(baseUrl + "/orders/" + encode(orderId) + ".json", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = get(baseUrl + "/orders/" + encode(orderId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				String status = context.getParameter("status", "any");
				HttpResponse<String> response = get(baseUrl + "/orders.json?limit=" + limit + "&status=" + encode(status), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String orderId = context.getParameter("orderId", "");
				Map<String, Object> order = new LinkedHashMap<>();
				order.put("id", orderId);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) order.put("email", email);
				String note = context.getParameter("note", "");
				if (!note.isEmpty()) order.put("note", note);
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) order.put("tags", tags);
				Map<String, Object> body = Map.of("order", order);
				HttpResponse<String> response = put(baseUrl + "/orders/" + encode(orderId) + ".json", body, headers);
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
				Map<String, Object> product = new LinkedHashMap<>();
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) product.put("title", title);
				String bodyHtml = context.getParameter("bodyHtml", "");
				if (!bodyHtml.isEmpty()) product.put("body_html", bodyHtml);
				String vendor = context.getParameter("vendor", "");
				if (!vendor.isEmpty()) product.put("vendor", vendor);
				String productType = context.getParameter("productType", "");
				if (!productType.isEmpty()) product.put("product_type", productType);
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) product.put("tags", tags);
				Map<String, Object> body = Map.of("product", product);
				HttpResponse<String> response = post(baseUrl + "/products.json", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String productId = context.getParameter("productId", "");
				HttpResponse<String> response = delete(baseUrl + "/products/" + encode(productId) + ".json", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String productId = context.getParameter("productId", "");
				HttpResponse<String> response = get(baseUrl + "/products/" + encode(productId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(baseUrl + "/products.json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String productId = context.getParameter("productId", "");
				Map<String, Object> product = new LinkedHashMap<>();
				product.put("id", productId);
				String title = context.getParameter("title", "");
				if (!title.isEmpty()) product.put("title", title);
				String bodyHtml = context.getParameter("bodyHtml", "");
				if (!bodyHtml.isEmpty()) product.put("body_html", bodyHtml);
				String vendor = context.getParameter("vendor", "");
				if (!vendor.isEmpty()) product.put("vendor", vendor);
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) product.put("tags", tags);
				Map<String, Object> body = Map.of("product", product);
				HttpResponse<String> response = put(baseUrl + "/products/" + encode(productId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown product operation: " + operation);
		};
	}

	// ---- Product Variant ----

	private Map<String, Object> handleProductVariant(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		String productId = context.getParameter("productId", "");
		return switch (operation) {
			case "create" -> {
				Map<String, Object> variant = new LinkedHashMap<>();
				String optionValue = context.getParameter("option1", "");
				if (!optionValue.isEmpty()) variant.put("option1", optionValue);
				String price = context.getParameter("price", "");
				if (!price.isEmpty()) variant.put("price", price);
				String sku = context.getParameter("sku", "");
				if (!sku.isEmpty()) variant.put("sku", sku);
				Map<String, Object> body = Map.of("variant", variant);
				HttpResponse<String> response = post(baseUrl + "/products/" + encode(productId) + "/variants.json", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String variantId = context.getParameter("variantId", "");
				HttpResponse<String> response = delete(baseUrl + "/products/" + encode(productId) + "/variants/" + encode(variantId) + ".json", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String variantId = context.getParameter("variantId", "");
				HttpResponse<String> response = get(baseUrl + "/variants/" + encode(variantId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(baseUrl + "/products/" + encode(productId) + "/variants.json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String variantId = context.getParameter("variantId", "");
				Map<String, Object> variant = new LinkedHashMap<>();
				variant.put("id", variantId);
				String price = context.getParameter("price", "");
				if (!price.isEmpty()) variant.put("price", price);
				String sku = context.getParameter("sku", "");
				if (!sku.isEmpty()) variant.put("sku", sku);
				String option1 = context.getParameter("option1", "");
				if (!option1.isEmpty()) variant.put("option1", option1);
				Map<String, Object> body = Map.of("variant", variant);
				HttpResponse<String> response = put(baseUrl + "/variants/" + encode(variantId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown productVariant operation: " + operation);
		};
	}

	// ---- Customer ----

	private Map<String, Object> handleCustomer(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> customer = new LinkedHashMap<>();
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) customer.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) customer.put("last_name", lastName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) customer.put("email", email);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) customer.put("phone", phone);
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) customer.put("tags", tags);
				Map<String, Object> body = Map.of("customer", customer);
				HttpResponse<String> response = post(baseUrl + "/customers.json", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(baseUrl + "/customers/" + encode(customerId) + ".json", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = get(baseUrl + "/customers/" + encode(customerId) + ".json", headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(baseUrl + "/customers.json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String customerId = context.getParameter("customerId", "");
				Map<String, Object> customer = new LinkedHashMap<>();
				customer.put("id", customerId);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) customer.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) customer.put("last_name", lastName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) customer.put("email", email);
				String tags = context.getParameter("tags", "");
				if (!tags.isEmpty()) customer.put("tags", tags);
				Map<String, Object> body = Map.of("customer", customer);
				HttpResponse<String> response = put(baseUrl + "/customers/" + encode(customerId) + ".json", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	// ---- Collection ----

	private Map<String, Object> handleCollection(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 50);
				HttpResponse<String> response = get(baseUrl + "/custom_collections.json?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown collection operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("order")
						.options(List.of(
								ParameterOption.builder().name("Order").value("order").build(),
								ParameterOption.builder().name("Product").value("product").build(),
								ParameterOption.builder().name("Product Variant").value("productVariant").build(),
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("Collection").value("collection").build()
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
						.name("orderId").displayName("Order ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productId").displayName("Product ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("variantId").displayName("Variant ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("customerId").displayName("Customer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("title").displayName("Title")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("bodyHtml").displayName("Body HTML")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("vendor").displayName("Vendor")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("productType").displayName("Product Type")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("tags").displayName("Tags")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated list of tags.").build(),
				NodeParameter.builder()
						.name("firstName").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastName").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("note").displayName("Note")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lineItems").displayName("Line Items (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of line items for order creation.").build(),
				NodeParameter.builder()
						.name("option1").displayName("Option Value")
						.type(ParameterType.STRING).defaultValue("")
						.description("Variant option value (e.g. size, color).").build(),
				NodeParameter.builder()
						.name("price").displayName("Price")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("sku").displayName("SKU")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("financialStatus").displayName("Financial Status")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Pending").value("pending").build(),
								ParameterOption.builder().name("Authorized").value("authorized").build(),
								ParameterOption.builder().name("Partially Paid").value("partially_paid").build(),
								ParameterOption.builder().name("Paid").value("paid").build(),
								ParameterOption.builder().name("Partially Refunded").value("partially_refunded").build(),
								ParameterOption.builder().name("Refunded").value("refunded").build(),
								ParameterOption.builder().name("Voided").value("voided").build()
						)).build(),
				NodeParameter.builder()
						.name("fulfillmentStatus").displayName("Fulfillment Status")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Fulfilled").value("fulfilled").build(),
								ParameterOption.builder().name("Partial").value("partial").build(),
								ParameterOption.builder().name("Restocked").value("restocked").build()
						)).build(),
				NodeParameter.builder()
						.name("status").displayName("Order Status")
						.type(ParameterType.OPTIONS).defaultValue("any")
						.options(List.of(
								ParameterOption.builder().name("Any").value("any").build(),
								ParameterOption.builder().name("Open").value("open").build(),
								ParameterOption.builder().name("Closed").value("closed").build(),
								ParameterOption.builder().name("Cancelled").value("cancelled").build()
						)).build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max results to return.").build()
		);
	}
}
