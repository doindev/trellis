package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.*;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;

/**
 * SyncroMSP — manage customers, tickets, contacts, and RMM alerts in SyncroMSP.
 */
@Node(
		type = "syncroMsp",
		displayName = "SyncroMSP",
		description = "Manage customers, tickets, contacts, and RMM alerts in SyncroMSP",
		category = "Customer Support",
		icon = "syncroMsp",
		credentials = {"syncroMspApi"}
)
public class SyncroMspNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String apiKey = (String) credentials.getOrDefault("apiKey", "");
		String subdomain = (String) credentials.getOrDefault("subdomain", "");
		String baseUrl = "https://" + subdomain + ".syncromsp.com/api/v1";

		String resource = context.getParameter("resource", "customer");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "customer" -> handleCustomer(context, baseUrl, headers, apiKey, operation);
					case "ticket" -> handleTicket(context, baseUrl, headers, apiKey, operation);
					case "contact" -> handleContact(context, baseUrl, headers, apiKey, operation);
					case "rmmAlert" -> handleRmmAlert(context, baseUrl, headers, apiKey, operation);
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

	private String appendApiKey(String url, String apiKey) {
		return url + (url.contains("?") ? "&" : "?") + "api_key=" + encode(apiKey);
	}

	private Map<String, Object> handleCustomer(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				String businessName = context.getParameter("businessName", "");
				if (!businessName.isEmpty()) body.put("business_name", businessName);
				String firstname = context.getParameter("firstname", "");
				if (!firstname.isEmpty()) body.put("firstname", firstname);
				String lastname = context.getParameter("lastname", "");
				if (!lastname.isEmpty()) body.put("lastname", lastname);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				HttpResponse<String> response = post(appendApiKey(baseUrl + "/customers", apiKey), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("customerId", "");
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/customers/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/customers", apiKey), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("customerId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String businessName = context.getParameter("businessName", "");
				if (!businessName.isEmpty()) body.put("business_name", businessName);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				HttpResponse<String> response = put(appendApiKey(baseUrl + "/customers/" + id, apiKey), body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(appendApiKey(baseUrl + "/customers/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	private Map<String, Object> handleTicket(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("subject", context.getParameter("subject", ""));
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) body.put("customer_id", toInt(customerId, 0));
				String problemType = context.getParameter("problemType", "");
				if (!problemType.isEmpty()) body.put("problem_type", problemType);
				String status = context.getParameter("status", "");
				if (!status.isEmpty()) body.put("status", status);
				HttpResponse<String> response = post(appendApiKey(baseUrl + "/tickets", apiKey), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/tickets/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/tickets", apiKey), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("ticketId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String subject = context.getParameter("subject", "");
				if (!subject.isEmpty()) body.put("subject", subject);
				String status = context.getParameter("status", "");
				if (!status.isEmpty()) body.put("status", status);
				HttpResponse<String> response = put(appendApiKey(baseUrl + "/tickets/" + id, apiKey), body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("ticketId", "");
				HttpResponse<String> response = delete(appendApiKey(baseUrl + "/tickets/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown ticket operation: " + operation);
		};
	}

	private Map<String, Object> handleContact(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("customer_id", toInt(context.getParameters().get("customerId"), 0));
				body.put("email", context.getParameter("email", ""));
				String name = context.getParameter("contactName", "");
				if (!name.isEmpty()) body.put("name", name);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) body.put("phone", phone);
				String notes = context.getParameter("notes", "");
				if (!notes.isEmpty()) body.put("notes", notes);
				HttpResponse<String> response = post(appendApiKey(baseUrl + "/contacts", apiKey), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("contactId", "");
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/contacts/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/contacts", apiKey), headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String id = context.getParameter("contactId", "");
				Map<String, Object> body = new LinkedHashMap<>();
				String name = context.getParameter("contactName", "");
				if (!name.isEmpty()) body.put("name", name);
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) body.put("email", email);
				HttpResponse<String> response = put(appendApiKey(baseUrl + "/contacts/" + id, apiKey), body, headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("contactId", "");
				HttpResponse<String> response = delete(appendApiKey(baseUrl + "/contacts/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown contact operation: " + operation);
		};
	}

	private Map<String, Object> handleRmmAlert(NodeExecutionContext context, String baseUrl, Map<String, String> headers, String apiKey, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("customer_id", toInt(context.getParameters().get("customerId"), 0));
				String assetId = context.getParameter("assetId", "");
				if (!assetId.isEmpty()) body.put("asset_id", toInt(assetId, 0));
				body.put("description", context.getParameter("alertDescription", ""));
				HttpResponse<String> response = post(appendApiKey(baseUrl + "/rmm_alerts", apiKey), body, headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String id = context.getParameter("alertId", "");
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/rmm_alerts/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				HttpResponse<String> response = get(appendApiKey(baseUrl + "/rmm_alerts", apiKey), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String id = context.getParameter("alertId", "");
				HttpResponse<String> response = delete(appendApiKey(baseUrl + "/rmm_alerts/" + id, apiKey), headers);
				yield parseResponse(response);
			}
			case "mute" -> {
				String id = context.getParameter("alertId", "");
				String muteFor = context.getParameter("muteFor", "");
				Map<String, Object> body = new LinkedHashMap<>();
				body.put("id", toInt(id, 0));
				body.put("mute_for", muteFor);
				HttpResponse<String> response = post(appendApiKey(baseUrl + "/rmm_alerts/" + id + "/mute", apiKey), body, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown rmmAlert operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("customer")
						.options(List.of(
								ParameterOption.builder().name("Contact").value("contact").build(),
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("RMM Alert").value("rmmAlert").build(),
								ParameterOption.builder().name("Ticket").value("ticket").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Mute").value("mute").build(),
								ParameterOption.builder().name("Update").value("update").build()
						)).build(),
				NodeParameter.builder()
						.name("customerId").displayName("Customer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("businessName").displayName("Business Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("firstname").displayName("First Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("lastname").displayName("Last Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("ticketId").displayName("Ticket ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subject").displayName("Subject")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("problemType").displayName("Problem Type")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("status").displayName("Status")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactId").displayName("Contact ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("contactName").displayName("Contact Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("notes").displayName("Notes")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("alertId").displayName("Alert ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("assetId").displayName("Asset ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("alertDescription").displayName("Alert Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("muteFor").displayName("Mute For")
						.type(ParameterType.STRING).defaultValue("")
						.description("Duration to mute the alert.").build()
		);
	}
}
