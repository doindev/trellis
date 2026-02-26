package io.trellis.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeInput;
import io.trellis.nodes.core.NodeOutput;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Magento 2 Node -- manage customers, invoices, orders, and products
 * via the Magento 2 REST API.
 */
@Slf4j
@Node(
	type = "magento2",
	displayName = "Magento 2",
	description = "Manage customers, invoices, orders, and products in Magento 2",
	category = "E-Commerce / Payments",
	icon = "magento",
	credentials = {"magento2Api"}
)
public class Magento2Node extends AbstractApiNode {

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
		List<NodeParameter> params = new ArrayList<>();

		// Resource selector
		params.add(NodeParameter.builder()
			.name("resource").displayName("Resource")
			.type(ParameterType.OPTIONS).required(true).defaultValue("product")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Customer").value("customer").description("Manage customers").build(),
				ParameterOption.builder().name("Invoice").value("invoice").description("Manage invoices").build(),
				ParameterOption.builder().name("Order").value("order").description("Manage orders").build(),
				ParameterOption.builder().name("Product").value("product").description("Manage products").build()
			)).build());

		// Customer operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a customer").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a customer").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a customer").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many customers").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a customer").build()
			)).build());

		// Invoice operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("invoice"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create an invoice for an order").build()
			)).build());

		// Order operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("order"))))
			.options(List.of(
				ParameterOption.builder().name("Cancel").value("cancel").description("Cancel an order").build(),
				ParameterOption.builder().name("Get").value("get").description("Get an order").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many orders").build(),
				ParameterOption.builder().name("Ship").value("ship").description("Ship an order").build()
			)).build());

		// Product operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a product").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a product").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a product").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get many products").build(),
				ParameterOption.builder().name("Update").value("update").description("Update a product").build()
			)).build());

		// Common parameters
		params.add(NodeParameter.builder()
			.name("customerId").displayName("Customer ID")
			.type(ParameterType.STRING)
			.description("The ID of the customer.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("get", "delete", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("orderId").displayName("Order ID")
			.type(ParameterType.STRING)
			.description("The ID of the order.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("order", "invoice"))))
			.build());

		params.add(NodeParameter.builder()
			.name("productSku").displayName("Product SKU")
			.type(ParameterType.STRING)
			.description("The SKU of the product.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("get", "delete", "update"))))
			.build());

		// Customer fields
		params.add(NodeParameter.builder()
			.name("email").displayName("Email")
			.type(ParameterType.STRING)
			.description("Customer email address.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("firstname").displayName("First Name")
			.type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("lastname").displayName("Last Name")
			.type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("groupId").displayName("Group ID")
			.type(ParameterType.NUMBER).defaultValue(1)
			.description("Customer group ID.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("customer"), "operation", List.of("create"))))
			.build());

		// Product fields
		params.add(NodeParameter.builder()
			.name("productName").displayName("Product Name")
			.type(ParameterType.STRING)
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sku").displayName("SKU")
			.type(ParameterType.STRING)
			.description("Stock Keeping Unit identifier.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("price").displayName("Price")
			.type(ParameterType.NUMBER)
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create", "update"))))
			.build());

		params.add(NodeParameter.builder()
			.name("attributeSetId").displayName("Attribute Set ID")
			.type(ParameterType.NUMBER).defaultValue(4)
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("productType").displayName("Product Type")
			.type(ParameterType.OPTIONS).defaultValue("simple")
			.displayOptions(Map.of("show", Map.of("resource", List.of("product"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Simple").value("simple").build(),
				ParameterOption.builder().name("Configurable").value("configurable").build(),
				ParameterOption.builder().name("Virtual").value("virtual").build(),
				ParameterOption.builder().name("Downloadable").value("downloadable").build(),
				ParameterOption.builder().name("Bundle").value("bundle").build(),
				ParameterOption.builder().name("Grouped").value("grouped").build()
			)).build());

		// Shipping fields
		params.add(NodeParameter.builder()
			.name("trackingNumber").displayName("Tracking Number")
			.type(ParameterType.STRING)
			.description("Shipment tracking number.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("order"), "operation", List.of("ship"))))
			.build());

		params.add(NodeParameter.builder()
			.name("carrierCode").displayName("Carrier Code")
			.type(ParameterType.STRING)
			.description("Shipping carrier code (e.g. ups, fedex, usps).")
			.displayOptions(Map.of("show", Map.of("resource", List.of("order"), "operation", List.of("ship"))))
			.build());

		params.add(NodeParameter.builder()
			.name("limit").displayName("Limit")
			.type(ParameterType.NUMBER).defaultValue(50)
			.description("Maximum number of results to return.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("additionalFields").displayName("Additional Fields (JSON)")
			.type(ParameterType.JSON).defaultValue("{}")
			.description("Additional fields as JSON.")
			.displayOptions(Map.of("show", Map.of("operation", List.of("create", "update"))))
			.build());

		return params;
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "product");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String baseUrl = getBaseUrl(credentials);
			Map<String, String> headers = authHeaders(credentials);

			return switch (resource) {
				case "customer" -> executeCustomer(context, operation, baseUrl, headers);
				case "invoice" -> executeInvoice(context, operation, baseUrl, headers);
				case "order" -> executeOrder(context, operation, baseUrl, headers);
				case "product" -> executeProduct(context, operation, baseUrl, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Magento 2 API error: " + e.getMessage(), e);
		}
	}

	// ========================= Customer Operations =========================

	private NodeExecutionResult executeCustomer(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String email = context.getParameter("email", "");
				String firstname = context.getParameter("firstname", "");
				String lastname = context.getParameter("lastname", "");
				int groupId = toInt(context.getParameter("groupId", 1), 1);
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> customer = new LinkedHashMap<>(parseJson(additionalJson));
				customer.put("email", email);
				customer.put("firstname", firstname);
				customer.put("lastname", lastname);
				customer.put("group_id", groupId);
				Map<String, Object> body = Map.of("customer", customer);
				HttpResponse<String> response = post(baseUrl + "/customers", body, headers);
				return toResult(response);
			}
			case "delete": {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(baseUrl + "/customers/" + encode(customerId), headers);
				return toDeleteResult(response, customerId);
			}
			case "get": {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = get(baseUrl + "/customers/" + encode(customerId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = baseUrl + "/customers/search?searchCriteria[pageSize]=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toSearchResult(response);
			}
			case "update": {
				String customerId = context.getParameter("customerId", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> customer = new LinkedHashMap<>(parseJson(additionalJson));
				customer.put("id", Integer.parseInt(customerId));
				putIfNotEmpty(customer, "email", context.getParameter("email", ""));
				putIfNotEmpty(customer, "firstname", context.getParameter("firstname", ""));
				putIfNotEmpty(customer, "lastname", context.getParameter("lastname", ""));
				Map<String, Object> body = Map.of("customer", customer);
				HttpResponse<String> response = put(baseUrl + "/customers/" + encode(customerId), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown customer operation: " + operation);
		}
	}

	// ========================= Invoice Operations =========================

	private NodeExecutionResult executeInvoice(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		if ("create".equals(operation)) {
			String orderId = context.getParameter("orderId", "");
			Map<String, Object> body = Map.of("capture", true, "notify", true);
			HttpResponse<String> response = post(baseUrl + "/order/" + encode(orderId) + "/invoice", body, headers);
			return toResult(response);
		}
		return NodeExecutionResult.error("Unknown invoice operation: " + operation);
	}

	// ========================= Order Operations =========================

	private NodeExecutionResult executeOrder(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "cancel": {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = post(baseUrl + "/orders/" + encode(orderId) + "/cancel", Map.of(), headers);
				return toResult(response);
			}
			case "get": {
				String orderId = context.getParameter("orderId", "");
				HttpResponse<String> response = get(baseUrl + "/orders/" + encode(orderId), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = baseUrl + "/orders?searchCriteria[pageSize]=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toSearchResult(response);
			}
			case "ship": {
				String orderId = context.getParameter("orderId", "");
				String trackingNumber = context.getParameter("trackingNumber", "");
				String carrierCode = context.getParameter("carrierCode", "");
				Map<String, Object> body = new LinkedHashMap<>();
				if (!trackingNumber.isEmpty() && !carrierCode.isEmpty()) {
					List<Map<String, Object>> tracks = List.of(Map.of(
						"track_number", trackingNumber,
						"carrier_code", carrierCode,
						"title", carrierCode
					));
					body.put("tracks", tracks);
				}
				body.put("notify", true);
				HttpResponse<String> response = post(baseUrl + "/order/" + encode(orderId) + "/ship", body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown order operation: " + operation);
		}
	}

	// ========================= Product Operations =========================

	private NodeExecutionResult executeProduct(NodeExecutionContext context, String operation,
			String baseUrl, Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String sku = context.getParameter("sku", "");
				String name = context.getParameter("productName", "");
				double price = toDouble(context.getParameter("price", 0), 0);
				int attrSetId = toInt(context.getParameter("attributeSetId", 4), 4);
				String type = context.getParameter("productType", "simple");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> product = new LinkedHashMap<>(parseJson(additionalJson));
				product.put("sku", sku);
				product.put("name", name);
				product.put("price", price);
				product.put("attribute_set_id", attrSetId);
				product.put("type_id", type);
				Map<String, Object> body = Map.of("product", product);
				HttpResponse<String> response = post(baseUrl + "/products", body, headers);
				return toResult(response);
			}
			case "delete": {
				String sku = context.getParameter("productSku", "");
				HttpResponse<String> response = delete(baseUrl + "/products/" + encode(sku), headers);
				return toDeleteResult(response, sku);
			}
			case "get": {
				String sku = context.getParameter("productSku", "");
				HttpResponse<String> response = get(baseUrl + "/products/" + encode(sku), headers);
				return toResult(response);
			}
			case "getAll": {
				int limit = toInt(context.getParameter("limit", 50), 50);
				String url = baseUrl + "/products?searchCriteria[pageSize]=" + limit;
				HttpResponse<String> response = get(url, headers);
				return toSearchResult(response);
			}
			case "update": {
				String sku = context.getParameter("productSku", "");
				String additionalJson = context.getParameter("additionalFields", "{}");
				Map<String, Object> product = new LinkedHashMap<>(parseJson(additionalJson));
				putIfNotEmpty(product, "name", context.getParameter("productName", ""));
				double price = toDouble(context.getParameter("price", 0), 0);
				if (price > 0) product.put("price", price);
				Map<String, Object> body = Map.of("product", product);
				HttpResponse<String> response = put(baseUrl + "/products/" + encode(sku), body, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown product operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	@Override
	protected String getBaseUrl(Map<String, Object> credentials) {
		String storeUrl = String.valueOf(credentials.getOrDefault("storeUrl",
				credentials.getOrDefault("host", "https://your-store.com")));
		if (storeUrl.endsWith("/")) {
			storeUrl = storeUrl.substring(0, storeUrl.length() - 1);
		}
		return storeUrl + "/rest/V1";
	}

	private Map<String, String> authHeaders(Map<String, Object> credentials) {
		Map<String, String> headers = new LinkedHashMap<>();
		String token = String.valueOf(credentials.getOrDefault("accessToken",
				credentials.getOrDefault("apiKey", "")));
		headers.put("Authorization", "Bearer " + token);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private void putIfNotEmpty(Map<String, Object> map, String key, String value) {
		if (value != null && !value.isEmpty()) {
			map.put(key, value);
		}
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return magentoError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true))));
		}
		// Magento may return a simple value (e.g. invoice ID as a number)
		if (!body.trim().startsWith("{") && !body.trim().startsWith("[")) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("result", body.trim()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	@SuppressWarnings("unchecked")
	private NodeExecutionResult toSearchResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return magentoError(response);
		}
		Map<String, Object> parsed = parseResponse(response);
		Object items = parsed.get("items");
		if (items instanceof List) {
			List<Map<String, Object>> results = new ArrayList<>();
			for (Object item : (List<?>) items) {
				if (item instanceof Map) {
					results.add(wrapInJson(item));
				}
			}
			return results.isEmpty() ? NodeExecutionResult.empty() : NodeExecutionResult.success(results);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toDeleteResult(HttpResponse<String> response, String id) throws Exception {
		if (response.statusCode() >= 400) {
			return magentoError(response);
		}
		return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "id", id))));
	}

	private NodeExecutionResult magentoError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Magento 2 API error (HTTP " + response.statusCode() + "): " + body);
	}
}
