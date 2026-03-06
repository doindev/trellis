package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * QuickBooks Online — manage bills, customers, employees, estimates, invoices,
 * items, payments, and vendors using the QuickBooks Online API.
 */
@Node(
		type = "quickBooks",
		displayName = "QuickBooks Online",
		description = "Manage bills, customers, invoices, payments, and more in QuickBooks Online",
		category = "E-Commerce / Payments",
		icon = "quickBooks",
		credentials = {"quickBooksOAuth2Api"}
)
public class QuickBooksNode extends AbstractApiNode {

	private static final String BASE_URL = "https://quickbooks.api.intuit.com/v3/company";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String companyId = (String) credentials.getOrDefault("companyId", "");

		String companyBaseUrl = BASE_URL + "/" + encode(companyId);

		String resource = context.getParameter("resource", "invoice");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "bill" -> handleBill(context, companyBaseUrl, headers, operation);
					case "customer" -> handleCustomer(context, companyBaseUrl, headers, operation);
					case "employee" -> handleEmployee(context, companyBaseUrl, headers, operation);
					case "estimate" -> handleEstimate(context, companyBaseUrl, headers, operation);
					case "invoice" -> handleInvoice(context, companyBaseUrl, headers, operation);
					case "item" -> handleItem(context, companyBaseUrl, headers, operation);
					case "payment" -> handlePayment(context, companyBaseUrl, headers, operation);
					case "vendor" -> handleVendor(context, companyBaseUrl, headers, operation);
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

	// ---- Bill ----

	private Map<String, Object> handleBill(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String vendorId = context.getParameter("vendorRefId", "");
				if (!vendorId.isEmpty()) body.put("VendorRef", Map.of("value", vendorId));
				String lineItemsJson = context.getParameter("lineItems", "[]");
				body.put("Line", parseJsonArray(lineItemsJson));
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) body.put("DueDate", dueDate);
				HttpResponse<String> response = post(baseUrl + "/bill", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String billId = context.getParameter("billId", "");
				String syncToken = context.getParameter("syncToken", "0");
				Map<String, Object> body = Map.of("Id", billId, "SyncToken", syncToken);
				HttpResponse<String> response = post(baseUrl + "/bill?operation=delete", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String billId = context.getParameter("billId", "");
				HttpResponse<String> response = get(baseUrl + "/bill/" + encode(billId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Bill MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String billId = context.getParameter("billId", "");
				// Fetch current bill first for SyncToken
				HttpResponse<String> getResponse = get(baseUrl + "/bill/" + encode(billId), headers);
				Map<String, Object> currentBill = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> billData = (Map<String, Object>) currentBill.getOrDefault("Bill", currentBill);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) billData.put("DueDate", dueDate);
				HttpResponse<String> response = post(baseUrl + "/bill", billData, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown bill operation: " + operation);
		};
	}

	// ---- Customer ----

	private Map<String, Object> handleCustomer(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) body.put("DisplayName", displayName);
				String givenName = context.getParameter("givenName", "");
				if (!givenName.isEmpty()) body.put("GivenName", givenName);
				String familyName = context.getParameter("familyName", "");
				if (!familyName.isEmpty()) body.put("FamilyName", familyName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("PrimaryEmailAddr", Map.of("Address", email));
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) body.put("PrimaryPhone", Map.of("FreeFormNumber", phone));
				String companyName = context.getParameter("companyName", "");
				if (!companyName.isEmpty()) body.put("CompanyName", companyName);
				HttpResponse<String> response = post(baseUrl + "/customer", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = get(baseUrl + "/customer/" + encode(customerId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Customer MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/customer/" + encode(customerId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> customerData = (Map<String, Object>) current.getOrDefault("Customer", current);
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) customerData.put("DisplayName", displayName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) customerData.put("PrimaryEmailAddr", Map.of("Address", email));
				HttpResponse<String> response = post(baseUrl + "/customer", customerData, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	// ---- Employee ----

	private Map<String, Object> handleEmployee(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String givenName = context.getParameter("givenName", "");
				if (!givenName.isEmpty()) body.put("GivenName", givenName);
				String familyName = context.getParameter("familyName", "");
				if (!familyName.isEmpty()) body.put("FamilyName", familyName);
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) body.put("DisplayName", displayName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("PrimaryEmailAddr", Map.of("Address", email));
				HttpResponse<String> response = post(baseUrl + "/employee", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String employeeId = context.getParameter("employeeId", "");
				HttpResponse<String> response = get(baseUrl + "/employee/" + encode(employeeId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Employee MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String employeeId = context.getParameter("employeeId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/employee/" + encode(employeeId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> employeeData = (Map<String, Object>) current.getOrDefault("Employee", current);
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) employeeData.put("DisplayName", displayName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) employeeData.put("PrimaryEmailAddr", Map.of("Address", email));
				HttpResponse<String> response = post(baseUrl + "/employee", employeeData, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown employee operation: " + operation);
		};
	}

	// ---- Estimate ----

	private Map<String, Object> handleEstimate(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String customerId = context.getParameter("customerRefId", "");
				if (!customerId.isEmpty()) body.put("CustomerRef", Map.of("value", customerId));
				String lineItemsJson = context.getParameter("lineItems", "[]");
				body.put("Line", parseJsonArray(lineItemsJson));
				HttpResponse<String> response = post(baseUrl + "/estimate", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String estimateId = context.getParameter("estimateId", "");
				String syncToken = context.getParameter("syncToken", "0");
				Map<String, Object> body = Map.of("Id", estimateId, "SyncToken", syncToken);
				HttpResponse<String> response = post(baseUrl + "/estimate?operation=delete", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String estimateId = context.getParameter("estimateId", "");
				HttpResponse<String> response = get(baseUrl + "/estimate/" + encode(estimateId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Estimate MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "send" -> {
				String estimateId = context.getParameter("estimateId", "");
				String sendTo = context.getParameter("sendTo", "");
				String url = baseUrl + "/estimate/" + encode(estimateId) + "/send";
				if (!sendTo.isEmpty()) url += "?sendTo=" + encode(sendTo);
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String estimateId = context.getParameter("estimateId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/estimate/" + encode(estimateId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> estimateData = (Map<String, Object>) current.getOrDefault("Estimate", current);
				String lineItemsJson = context.getParameter("lineItems", "");
				if (!lineItemsJson.isEmpty()) estimateData.put("Line", parseJsonArray(lineItemsJson));
				HttpResponse<String> response = post(baseUrl + "/estimate", estimateData, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown estimate operation: " + operation);
		};
	}

	// ---- Invoice ----

	private Map<String, Object> handleInvoice(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String customerId = context.getParameter("customerRefId", "");
				if (!customerId.isEmpty()) body.put("CustomerRef", Map.of("value", customerId));
				String lineItemsJson = context.getParameter("lineItems", "[]");
				body.put("Line", parseJsonArray(lineItemsJson));
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) body.put("DueDate", dueDate);
				HttpResponse<String> response = post(baseUrl + "/invoice", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				String syncToken = context.getParameter("syncToken", "0");
				Map<String, Object> body = Map.of("Id", invoiceId, "SyncToken", syncToken);
				HttpResponse<String> response = post(baseUrl + "/invoice?operation=delete", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = get(baseUrl + "/invoice/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Invoice MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "send" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				String sendTo = context.getParameter("sendTo", "");
				String url = baseUrl + "/invoice/" + encode(invoiceId) + "/send";
				if (!sendTo.isEmpty()) url += "?sendTo=" + encode(sendTo);
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/invoice/" + encode(invoiceId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> invoiceData = (Map<String, Object>) current.getOrDefault("Invoice", current);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) invoiceData.put("DueDate", dueDate);
				String lineItemsJson = context.getParameter("lineItems", "");
				if (!lineItemsJson.isEmpty()) invoiceData.put("Line", parseJsonArray(lineItemsJson));
				HttpResponse<String> response = post(baseUrl + "/invoice", invoiceData, headers);
				yield parseResponse(response);
			}
			case "void" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/invoice/" + encode(invoiceId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> invoiceData = (Map<String, Object>) current.getOrDefault("Invoice", current);
				Map<String, Object> body = new LinkedHashMap<>(invoiceData);
				HttpResponse<String> response = post(baseUrl + "/invoice?operation=void", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoice operation: " + operation);
		};
	}

	// ---- Item ----

	private Map<String, Object> handleItem(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String itemId = context.getParameter("itemId", "");
				HttpResponse<String> response = get(baseUrl + "/item/" + encode(itemId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Item MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown item operation: " + operation);
		};
	}

	// ---- Payment ----

	private Map<String, Object> handlePayment(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String customerId = context.getParameter("customerRefId", "");
				if (!customerId.isEmpty()) body.put("CustomerRef", Map.of("value", customerId));
				String totalAmt = context.getParameter("totalAmount", "");
				if (!totalAmt.isEmpty()) body.put("TotalAmt", Double.parseDouble(totalAmt));
				HttpResponse<String> response = post(baseUrl + "/payment", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String paymentId = context.getParameter("paymentId", "");
				String syncToken = context.getParameter("syncToken", "0");
				Map<String, Object> body = Map.of("Id", paymentId, "SyncToken", syncToken);
				HttpResponse<String> response = post(baseUrl + "/payment?operation=delete", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> response = get(baseUrl + "/payment/" + encode(paymentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Payment MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "send" -> {
				String paymentId = context.getParameter("paymentId", "");
				String sendTo = context.getParameter("sendTo", "");
				String url = baseUrl + "/payment/" + encode(paymentId) + "/send";
				if (!sendTo.isEmpty()) url += "?sendTo=" + encode(sendTo);
				HttpResponse<String> response = post(url, Map.of(), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/payment/" + encode(paymentId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> paymentData = (Map<String, Object>) current.getOrDefault("Payment", current);
				String totalAmt = context.getParameter("totalAmount", "");
				if (!totalAmt.isEmpty()) paymentData.put("TotalAmt", Double.parseDouble(totalAmt));
				HttpResponse<String> response = post(baseUrl + "/payment", paymentData, headers);
				yield parseResponse(response);
			}
			case "void" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/payment/" + encode(paymentId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> paymentData = (Map<String, Object>) current.getOrDefault("Payment", current);
				Map<String, Object> body = new LinkedHashMap<>(paymentData);
				HttpResponse<String> response = post(baseUrl + "/payment?operation=void", body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payment operation: " + operation);
		};
	}

	// ---- Vendor ----

	private Map<String, Object> handleVendor(NodeExecutionContext context, String baseUrl,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) body.put("DisplayName", displayName);
				String givenName = context.getParameter("givenName", "");
				if (!givenName.isEmpty()) body.put("GivenName", givenName);
				String familyName = context.getParameter("familyName", "");
				if (!familyName.isEmpty()) body.put("FamilyName", familyName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("PrimaryEmailAddr", Map.of("Address", email));
				String companyName = context.getParameter("companyName", "");
				if (!companyName.isEmpty()) body.put("CompanyName", companyName);
				HttpResponse<String> response = post(baseUrl + "/vendor", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String vendorId = context.getParameter("vendorId", "");
				HttpResponse<String> response = get(baseUrl + "/vendor/" + encode(vendorId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String query = "SELECT * FROM Vendor MAXRESULTS " + limit;
				HttpResponse<String> response = get(baseUrl + "/query?query=" + encode(query), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String vendorId = context.getParameter("vendorId", "");
				HttpResponse<String> getResponse = get(baseUrl + "/vendor/" + encode(vendorId), headers);
				Map<String, Object> current = parseResponse(getResponse);
				@SuppressWarnings("unchecked")
				Map<String, Object> vendorData = (Map<String, Object>) current.getOrDefault("Vendor", current);
				String displayName = context.getParameter("displayName", "");
				if (!displayName.isEmpty()) vendorData.put("DisplayName", displayName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) vendorData.put("PrimaryEmailAddr", Map.of("Address", email));
				HttpResponse<String> response = post(baseUrl + "/vendor", vendorData, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown vendor operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("invoice")
						.options(List.of(
								ParameterOption.builder().name("Bill").value("bill").build(),
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("Employee").value("employee").build(),
								ParameterOption.builder().name("Estimate").value("estimate").build(),
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Item").value("item").build(),
								ParameterOption.builder().name("Payment").value("payment").build(),
								ParameterOption.builder().name("Vendor").value("vendor").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Send").value("send").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Void").value("void").build()
						)).build(),
				NodeParameter.builder()
						.name("billId").displayName("Bill ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("customerId").displayName("Customer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("customerRefId").displayName("Customer Ref ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("QuickBooks Customer reference ID.").build(),
				NodeParameter.builder()
						.name("employeeId").displayName("Employee ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("estimateId").displayName("Estimate ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceId").displayName("Invoice ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("itemId").displayName("Item ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentId").displayName("Payment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("vendorId").displayName("Vendor ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("vendorRefId").displayName("Vendor Ref ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("QuickBooks Vendor reference ID for bills.").build(),
				NodeParameter.builder()
						.name("syncToken").displayName("Sync Token")
						.type(ParameterType.STRING).defaultValue("0")
						.description("Required for delete operations.").build(),
				NodeParameter.builder()
						.name("displayName").displayName("Display Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("givenName").displayName("Given Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("familyName").displayName("Family Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("companyName").displayName("Company Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("totalAmount").displayName("Total Amount")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("dueDate").displayName("Due Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Due date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("sendTo").displayName("Send To Email")
						.type(ParameterType.STRING).defaultValue("")
						.description("Email address to send the document to.").build(),
				NodeParameter.builder()
						.name("lineItems").displayName("Line Items (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of line item objects.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
