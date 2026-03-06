package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * Chargebee — manage subscriptions, customers, and invoices using the Chargebee API.
 */
@Node(
		type = "chargebee",
		displayName = "Chargebee",
		description = "Manage subscriptions and invoices in Chargebee",
		category = "E-Commerce / Payments",
		icon = "chargebee",
		credentials = {"chargebeeApi"}
)
public class ChargebeeNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String accountName = (String) credentials.getOrDefault("accountName", "");

		String baseUrl = "https://" + accountName + ".chargebee.com/api/v2";

		String resource = context.getParameter("resource", "customer");
		String operation = context.getParameter("operation", "create");

		// Chargebee uses Basic Auth with apiKey as username and empty password
		String basicAuth = Base64.getEncoder().encodeToString((apiKey + ":").getBytes());
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Basic " + basicAuth);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "customer" -> handleCustomer(context, baseUrl, headers, operation);
					case "invoice" -> handleInvoice(context, baseUrl, headers, operation);
					case "subscription" -> handleSubscription(context, baseUrl, headers, operation);
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

	private Map<String, Object> handleCustomer(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String firstName = context.getParameter("firstName", "");
				if (!firstName.isEmpty()) body.put("first_name", firstName);
				String lastName = context.getParameter("lastName", "");
				if (!lastName.isEmpty()) body.put("last_name", lastName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				String company = context.getParameter("company", "");
				if (!company.isEmpty()) body.put("company", company);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) body.put("phone", phone);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(baseUrl + "/customers", buildFormBody(body), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	private Map<String, Object> handleInvoice(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "list" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				HttpResponse<String> response = get(baseUrl + "/invoices?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "pdfUrl" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = post(baseUrl + "/invoices/" + encode(invoiceId) + "/pdf", "", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoice operation: " + operation);
		};
	}

	private Map<String, Object> handleSubscription(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String operation) throws Exception {
		String subscriptionId = context.getParameter("subscriptionId", "");
		return switch (operation) {
			case "cancel" -> {
				HttpResponse<String> response = post(baseUrl + "/subscriptions/" + encode(subscriptionId) + "/cancel", "", headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				HttpResponse<String> response = post(baseUrl + "/subscriptions/" + encode(subscriptionId) + "/delete", "", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown subscription operation: " + operation);
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
						.type(ParameterType.OPTIONS).defaultValue("customer")
						.options(List.of(
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Subscription").value("subscription").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("create")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("List").value("list").build(),
								ParameterOption.builder().name("PDF URL").value("pdfUrl").build(),
								ParameterOption.builder().name("Cancel").value("cancel").build(),
								ParameterOption.builder().name("Delete").value("delete").build()
						)).build(),
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
						.name("company").displayName("Company")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceId").displayName("Invoice ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subscriptionId").displayName("Subscription ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max results to return.").build()
		);
	}
}
