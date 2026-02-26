package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Invoice Ninja — manage invoices, clients, tasks, payments, expenses, and quotes
 * using the Invoice Ninja API.
 */
@Node(
		type = "invoiceNinja",
		displayName = "Invoice Ninja",
		description = "Manage invoices and clients in Invoice Ninja",
		category = "E-Commerce",
		icon = "invoiceNinja",
		credentials = {"invoiceNinjaApi"}
)
public class InvoiceNinjaNode extends AbstractApiNode {

	private static final String DEFAULT_BASE_URL = "https://invoicing.co/api/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiToken = (String) credentials.getOrDefault("apiToken", "");
		String baseUrl = (String) credentials.getOrDefault("url", "");
		if (baseUrl.isEmpty()) {
			baseUrl = DEFAULT_BASE_URL;
		}
		// Remove trailing slash if present
		if (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}

		String resource = context.getParameter("resource", "client");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("X-Ninja-Token", apiToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "client" -> handleClient(context, baseUrl, headers, operation);
					case "invoice" -> handleInvoice(context, baseUrl, headers, operation);
					case "task" -> handleTask(context, baseUrl, headers, operation);
					case "payment" -> handlePayment(context, baseUrl, headers, operation);
					case "expense" -> handleExpense(context, baseUrl, headers, operation);
					case "quote" -> handleQuote(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleClient(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("clientName", "");
				if (!name.isEmpty()) body.put("name", name);
				String contactEmail = context.getParameter("contactEmail", "");
				if (!contactEmail.isEmpty()) {
					body.put("contacts", List.of(Map.of("email", contactEmail)));
				}
				String idNumber = context.getParameter("idNumber", "");
				if (!idNumber.isEmpty()) body.put("id_number", idNumber);
				HttpResponse<String> response = post(baseUrl + "/clients", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = get(baseUrl + "/clients/" + encode(clientId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/clients?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String clientId = context.getParameter("clientId", "");
				HttpResponse<String> response = delete(baseUrl + "/clients/" + encode(clientId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown client operation: " + operation);
		};
	}

	private Map<String, Object> handleInvoice(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String clientId = context.getParameter("clientId", "");
				if (!clientId.isEmpty()) body.put("client_id", clientId);
				String invoiceItems = context.getParameter("invoiceItems", "");
				if (!invoiceItems.isEmpty()) {
					body.put("line_items", parseJsonArray(invoiceItems));
				}
				HttpResponse<String> response = post(baseUrl + "/invoices", body, headers);
				yield parseResponse(response);
			}
			case "email" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				Map<String, Object> body = Map.of("entity", "invoice", "entity_id", invoiceId);
				HttpResponse<String> response = post(baseUrl + "/emails", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = get(baseUrl + "/invoices/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/invoices?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = delete(baseUrl + "/invoices/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoice operation: " + operation);
		};
	}

	private Map<String, Object> handleTask(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) body.put("description", description);
				String clientId = context.getParameter("clientId", "");
				if (!clientId.isEmpty()) body.put("client_id", clientId);
				HttpResponse<String> response = post(baseUrl + "/tasks", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = get(baseUrl + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/tasks?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String taskId = context.getParameter("taskId", "");
				HttpResponse<String> response = delete(baseUrl + "/tasks/" + encode(taskId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown task operation: " + operation);
		};
	}

	private Map<String, Object> handlePayment(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String clientId = context.getParameter("clientId", "");
				if (!clientId.isEmpty()) body.put("client_id", clientId);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) body.put("amount", Double.parseDouble(amount));
				String invoiceId = context.getParameter("invoiceId", "");
				if (!invoiceId.isEmpty()) {
					body.put("invoices", List.of(Map.of("invoice_id", invoiceId, "amount", Double.parseDouble(amount))));
				}
				HttpResponse<String> response = post(baseUrl + "/payments", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> response = get(baseUrl + "/payments/" + encode(paymentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/payments?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String paymentId = context.getParameter("paymentId", "");
				HttpResponse<String> response = delete(baseUrl + "/payments/" + encode(paymentId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payment operation: " + operation);
		};
	}

	private Map<String, Object> handleExpense(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String clientId = context.getParameter("clientId", "");
				if (!clientId.isEmpty()) body.put("client_id", clientId);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) body.put("amount", Double.parseDouble(amount));
				String publicNotes = context.getParameter("publicNotes", "");
				if (!publicNotes.isEmpty()) body.put("public_notes", publicNotes);
				HttpResponse<String> response = post(baseUrl + "/expenses", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String expenseId = context.getParameter("expenseId", "");
				HttpResponse<String> response = get(baseUrl + "/expenses/" + encode(expenseId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/expenses?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String expenseId = context.getParameter("expenseId", "");
				HttpResponse<String> response = delete(baseUrl + "/expenses/" + encode(expenseId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown expense operation: " + operation);
		};
	}

	private Map<String, Object> handleQuote(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String clientId = context.getParameter("clientId", "");
				if (!clientId.isEmpty()) body.put("client_id", clientId);
				String quoteItems = context.getParameter("quoteItems", "");
				if (!quoteItems.isEmpty()) {
					body.put("line_items", parseJsonArray(quoteItems));
				}
				HttpResponse<String> response = post(baseUrl + "/quotes", body, headers);
				yield parseResponse(response);
			}
			case "email" -> {
				String quoteId = context.getParameter("quoteId", "");
				Map<String, Object> body = Map.of("entity", "quote", "entity_id", quoteId);
				HttpResponse<String> response = post(baseUrl + "/emails", body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String quoteId = context.getParameter("quoteId", "");
				HttpResponse<String> response = get(baseUrl + "/quotes/" + encode(quoteId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/quotes?per_page=" + limit, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String quoteId = context.getParameter("quoteId", "");
				HttpResponse<String> response = delete(baseUrl + "/quotes/" + encode(quoteId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown quote operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("client")
						.options(List.of(
								ParameterOption.builder().name("Client").value("client").build(),
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Task").value("task").build(),
								ParameterOption.builder().name("Payment").value("payment").build(),
								ParameterOption.builder().name("Expense").value("expense").build(),
								ParameterOption.builder().name("Quote").value("quote").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Email").value("email").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build()
						)).build(),
				NodeParameter.builder()
						.name("clientId").displayName("Client ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("clientName").displayName("Client Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactEmail").displayName("Contact Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("idNumber").displayName("ID Number")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceId").displayName("Invoice ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceItems").displayName("Invoice Items (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("Line items as JSON array.").build(),
				NodeParameter.builder()
						.name("quoteId").displayName("Quote ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("quoteItems").displayName("Quote Items (JSON)")
						.type(ParameterType.JSON).defaultValue("")
						.description("Line items as JSON array.").build(),
				NodeParameter.builder()
						.name("taskId").displayName("Task ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentId").displayName("Payment ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("expenseId").displayName("Expense ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("amount").displayName("Amount")
						.type(ParameterType.STRING).defaultValue("")
						.description("Payment or expense amount.").build(),
				NodeParameter.builder()
						.name("publicNotes").displayName("Public Notes")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
