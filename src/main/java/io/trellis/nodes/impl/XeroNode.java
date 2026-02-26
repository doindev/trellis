package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Xero — manage contacts, invoices, and payments using the Xero Accounting API.
 * Requires OAuth2 Bearer token and Xero-Tenant-Id header.
 */
@Node(
		type = "xero",
		displayName = "Xero",
		description = "Manage contacts, invoices, and payments in Xero",
		category = "Finance",
		icon = "xero",
		credentials = {"xeroOAuth2Api"}
)
public class XeroNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.xero.com/api.xro/2.0";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = (String) credentials.getOrDefault("accessToken", "");
		String tenantId = (String) credentials.getOrDefault("tenantId", "");

		String resource = context.getParameter("resource", "contact");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Xero-Tenant-Id", tenantId);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "contact" -> handleContact(context, headers, operation);
					case "invoice" -> handleInvoice(context, headers, operation);
					case "payment" -> handlePayment(context, headers, operation);
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

	// ---- Contact ----

	private Map<String, Object> handleContact(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> contact = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) contact.put("Name", name);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("FirstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("LastName", lastName);
				String emailAddress = context.getParameter("email", "");
				if (!emailAddress.isEmpty()) contact.put("EmailAddress", emailAddress);
				Map<String, Object> body = Map.of("Contacts", List.of(contact));
				HttpResponse<String> response = post(BASE_URL + "/Contacts", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String contactId = context.getParameter("contactId", "");
				HttpResponse<String> response = get(BASE_URL + "/Contacts/" + encode(contactId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/Contacts", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String contactId = context.getParameter("contactId", "");
				Map<String, Object> contact = new LinkedHashMap<>();
				contact.put("ContactID", contactId);
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) contact.put("Name", name);
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) contact.put("FirstName", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) contact.put("LastName", lastName);
				String emailAddress = context.getParameter("email", "");
				if (!emailAddress.isEmpty()) contact.put("EmailAddress", emailAddress);
				Map<String, Object> body = Map.of("Contacts", List.of(contact));
				HttpResponse<String> response = post(BASE_URL + "/Contacts/" + encode(contactId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	// ---- Invoice ----

	private Map<String, Object> handleInvoice(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> invoice = new LinkedHashMap<>();
				String type = context.getParameter("invoiceType", "ACCREC");
				invoice.put("Type", type);
				String contactId = context.getParameter("contactId", "");
				if (!contactId.isEmpty()) {
					invoice.put("Contact", Map.of("ContactID", contactId));
				}
				String lineItemsJson = context.getParameter("lineItems", "[]");
				invoice.put("LineItems", parseJsonArray(lineItemsJson));
				String date = context.getParameter("date", "");
				if (!date.isEmpty()) invoice.put("Date", date);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) invoice.put("DueDate", dueDate);
				String reference = context.getParameter("reference", "");
				if (!reference.isEmpty()) invoice.put("Reference", reference);
				Map<String, Object> body = Map.of("Invoices", List.of(invoice));
				HttpResponse<String> response = post(BASE_URL + "/Invoices", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				Map<String, Object> invoice = new LinkedHashMap<>();
				invoice.put("InvoiceID", invoiceId);
				invoice.put("Status", "DELETED");
				Map<String, Object> body = Map.of("Invoices", List.of(invoice));
				HttpResponse<String> response = post(BASE_URL + "/Invoices/" + encode(invoiceId), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = get(BASE_URL + "/Invoices/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/Invoices", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				Map<String, Object> invoice = new LinkedHashMap<>();
				invoice.put("InvoiceID", invoiceId);
				String reference = context.getParameter("reference", "");
				if (!reference.isEmpty()) invoice.put("Reference", reference);
				String dueDate = context.getParameter("dueDate", "");
				if (!dueDate.isEmpty()) invoice.put("DueDate", dueDate);
				String status = context.getParameter("invoiceStatus", "");
				if (!status.isEmpty()) invoice.put("Status", status);
				Map<String, Object> body = Map.of("Invoices", List.of(invoice));
				HttpResponse<String> response = post(BASE_URL + "/Invoices/" + encode(invoiceId), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoice operation: " + operation);
		};
	}

	// ---- Payment ----

	private Map<String, Object> handlePayment(NodeExecutionContext context,
			Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> payment = new LinkedHashMap<>();
				String invoiceId = context.getParameter("invoiceId", "");
				if (!invoiceId.isEmpty()) {
					payment.put("Invoice", Map.of("InvoiceID", invoiceId));
				}
				String accountId = context.getParameter("accountId", "");
				if (!accountId.isEmpty()) {
					payment.put("Account", Map.of("AccountID", accountId));
				}
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) payment.put("Amount", Double.parseDouble(amount));
				String date = context.getParameter("date", "");
				if (!date.isEmpty()) payment.put("Date", date);
				Map<String, Object> body = Map.of("Payments", List.of(payment));
				HttpResponse<String> response = post(BASE_URL + "/Payments", body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String paymentId = context.getParameter("paymentId", "");
				Map<String, Object> payment = Map.of("PaymentID", paymentId, "Status", "DELETED");
				Map<String, Object> body = Map.of("Payments", List.of(payment));
				HttpResponse<String> response = post(BASE_URL + "/Payments/" + encode(paymentId), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> response = get(BASE_URL + "/Payments/" + encode(paymentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/Payments", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payment operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("contact")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Payment").value("payment").build()
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
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceId").displayName("Invoice ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentId").displayName("Payment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("accountId").displayName("Account ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
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
						.name("invoiceType").displayName("Invoice Type")
						.type(ParameterType.OPTIONS).defaultValue("ACCREC")
						.options(List.of(
								ParameterOption.builder().name("Accounts Receivable").value("ACCREC").build(),
								ParameterOption.builder().name("Accounts Payable").value("ACCPAY").build()
						)).build(),
				NodeParameter.builder()
						.name("invoiceStatus").displayName("Invoice Status")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Draft").value("DRAFT").build(),
								ParameterOption.builder().name("Submitted").value("SUBMITTED").build(),
								ParameterOption.builder().name("Authorised").value("AUTHORISED").build(),
								ParameterOption.builder().name("Voided").value("VOIDED").build()
						)).build(),
				NodeParameter.builder()
						.name("lineItems").displayName("Line Items (JSON)")
						.type(ParameterType.JSON).defaultValue("[]")
						.description("JSON array of line item objects.").build(),
				NodeParameter.builder()
						.name("date").displayName("Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("dueDate").displayName("Due Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Due date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("reference").displayName("Reference")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("amount").displayName("Amount")
						.type(ParameterType.STRING).defaultValue("").build()
		);
	}
}
