package io.cwc.nodes.impl;

import java.net.http.HttpResponse;
import java.util.*;

import io.cwc.nodes.annotation.Node;
import io.cwc.nodes.base.AbstractApiNode;
import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.nodes.core.NodeInput;
import io.cwc.nodes.core.NodeOutput;
import io.cwc.nodes.core.NodeParameter;
import io.cwc.nodes.core.NodeParameter.ParameterOption;
import io.cwc.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Wise Node -- manage accounts, profiles, quotes, recipients, and transfers
 * via the Wise (TransferWise) API.
 */
@Slf4j
@Node(
	type = "wise",
	displayName = "Wise",
	description = "Manage Wise accounts, profiles, quotes, recipients, and transfers",
	category = "Finance",
	icon = "wise",
	credentials = {"wiseApi"}
)
public class WiseNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.transferwise.com";

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
			.type(ParameterType.OPTIONS).required(true).defaultValue("transfer")
			.noDataExpression(true)
			.options(List.of(
				ParameterOption.builder().name("Account").value("account").description("Manage borderless accounts").build(),
				ParameterOption.builder().name("Profile").value("profile").description("Manage profiles").build(),
				ParameterOption.builder().name("Quote").value("quote").description("Manage quotes").build(),
				ParameterOption.builder().name("Recipient").value("recipient").description("Manage recipients").build(),
				ParameterOption.builder().name("Transfer").value("transfer").description("Manage transfers").build()
			)).build());

		// Operation selectors
		addOperationSelectors(params);

		// Resource-specific parameters
		addAccountParameters(params);
		addProfileParameters(params);
		addQuoteParameters(params);
		addRecipientParameters(params);
		addTransferParameters(params);

		return params;
	}

	// ========================= Operation Selectors =========================

	private void addOperationSelectors(List<NodeParameter> params) {
		// Account operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"))))
			.options(List.of(
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all borderless accounts").build()
			)).build());

		// Profile operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("profile"))))
			.options(List.of(
				ParameterOption.builder().name("Get").value("get").description("Get a profile").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all profiles").build()
			)).build());

		// Quote operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("create")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a quote").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a quote").build()
			)).build());

		// Recipient operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a recipient").build(),
				ParameterOption.builder().name("Delete").value("delete").description("Delete a recipient").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a recipient").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all recipients").build()
			)).build());

		// Transfer operations
		params.add(NodeParameter.builder()
			.name("operation").displayName("Operation")
			.type(ParameterType.OPTIONS).required(true).defaultValue("getAll")
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"))))
			.options(List.of(
				ParameterOption.builder().name("Create").value("create").description("Create a transfer").build(),
				ParameterOption.builder().name("Execute").value("execute").description("Execute (fund) a transfer").build(),
				ParameterOption.builder().name("Get").value("get").description("Get a transfer").build(),
				ParameterOption.builder().name("Get Many").value("getAll").description("Get all transfers").build()
			)).build());
	}

	// ========================= Account Parameters =========================

	private void addAccountParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("profileId").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.description("The profile ID to use for this operation.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("account"))))
			.build());
	}

	// ========================= Profile Parameters =========================

	private void addProfileParameters(List<NodeParameter> params) {
		params.add(NodeParameter.builder()
			.name("profileIdGet").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("profile"), "operation", List.of("get"))))
			.build());
	}

	// ========================= Quote Parameters =========================

	private void addQuoteParameters(List<NodeParameter> params) {
		// Quote > Create
		params.add(NodeParameter.builder()
			.name("quoteProfileId").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sourceCurrency").displayName("Source Currency")
			.type(ParameterType.STRING).required(true)
			.placeHolder("USD")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("targetCurrency").displayName("Target Currency")
			.type(ParameterType.STRING).required(true)
			.placeHolder("EUR")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("sourceAmount").displayName("Source Amount")
			.type(ParameterType.NUMBER)
			.description("The amount in the source currency. Either source or target amount must be specified.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("targetAmount").displayName("Target Amount")
			.type(ParameterType.NUMBER)
			.description("The amount in the target currency. Either source or target amount must be specified.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"), "operation", List.of("create"))))
			.build());

		// Quote > Get
		params.add(NodeParameter.builder()
			.name("quoteId").displayName("Quote ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("quote"), "operation", List.of("get"))))
			.build());
	}

	// ========================= Recipient Parameters =========================

	private void addRecipientParameters(List<NodeParameter> params) {
		// Recipient > Create: fields JSON
		params.add(NodeParameter.builder()
			.name("recipientProfileId").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"))))
			.build());

		params.add(NodeParameter.builder()
			.name("recipientCurrency").displayName("Currency")
			.type(ParameterType.STRING).required(true)
			.placeHolder("EUR")
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("recipientType").displayName("Type")
			.type(ParameterType.OPTIONS).required(true).defaultValue("email")
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.options(List.of(
				ParameterOption.builder().name("Email").value("email").build(),
				ParameterOption.builder().name("Sort Code").value("sort_code").build(),
				ParameterOption.builder().name("IBAN").value("iban").build(),
				ParameterOption.builder().name("Swift Code").value("swift_code").build(),
				ParameterOption.builder().name("ABA").value("aba").build(),
				ParameterOption.builder().name("Fedwire").value("fedwire_local").build()
			)).build());

		params.add(NodeParameter.builder()
			.name("recipientAccountHolderName").displayName("Account Holder Name")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("recipientDetails").displayName("Details (JSON)")
			.type(ParameterType.JSON).required(true)
			.description("Recipient bank details as JSON. The required fields depend on the recipient type and currency.")
			.placeHolder("{\"email\": \"recipient@example.com\"}")
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("create"))))
			.build());

		// Recipient > Get/Delete: ID
		params.add(NodeParameter.builder()
			.name("recipientId").displayName("Recipient ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("recipient"), "operation", List.of("get", "delete"))))
			.build());
	}

	// ========================= Transfer Parameters =========================

	private void addTransferParameters(List<NodeParameter> params) {
		// Transfer > Create
		params.add(NodeParameter.builder()
			.name("transferTargetAccountId").displayName("Target Account (Recipient) ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("transferQuoteUuid").displayName("Quote UUID")
			.type(ParameterType.STRING).required(true)
			.description("The UUID of the quote to use for this transfer.")
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("create"))))
			.build());

		params.add(NodeParameter.builder()
			.name("transferReference").displayName("Reference")
			.type(ParameterType.STRING)
			.description("A reference text to attach to the transfer.")
			.placeHolder("Invoice #1234")
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("create"))))
			.build());

		// Transfer > Execute
		params.add(NodeParameter.builder()
			.name("transferIdExecute").displayName("Transfer ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("execute"))))
			.build());

		// Transfer > Get
		params.add(NodeParameter.builder()
			.name("transferId").displayName("Transfer ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("get"))))
			.build());

		// Transfer > GetAll
		params.add(NodeParameter.builder()
			.name("transferProfileId").displayName("Profile ID")
			.type(ParameterType.STRING).required(true)
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("getAll"))))
			.build());

		params.add(NodeParameter.builder()
			.name("transferFilters").displayName("Filters")
			.type(ParameterType.COLLECTION)
			.displayOptions(Map.of("show", Map.of("resource", List.of("transfer"), "operation", List.of("getAll"))))
			.nestedParameters(List.of(
				NodeParameter.builder().name("limit").displayName("Limit").type(ParameterType.NUMBER)
					.defaultValue(10).description("Maximum number of transfers to return.").build(),
				NodeParameter.builder().name("offset").displayName("Offset").type(ParameterType.NUMBER)
					.defaultValue(0).description("Offset for pagination.").build(),
				NodeParameter.builder().name("status").displayName("Status").type(ParameterType.OPTIONS)
					.options(List.of(
						ParameterOption.builder().name("All").value("").build(),
						ParameterOption.builder().name("Incoming Payment Waiting").value("incoming_payment_waiting").build(),
						ParameterOption.builder().name("Processing").value("processing").build(),
						ParameterOption.builder().name("Funds Converted").value("funds_converted").build(),
						ParameterOption.builder().name("Outgoing Payment Sent").value("outgoing_payment_sent").build(),
						ParameterOption.builder().name("Cancelled").value("cancelled").build(),
						ParameterOption.builder().name("Funds Refunded").value("funds_refunded").build(),
						ParameterOption.builder().name("Bounced Back").value("bounced_back").build()
					)).build()
			)).build());
	}

	// ========================= Execute =========================

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "transfer");
		String operation = context.getParameter("operation", "getAll");
		Map<String, Object> credentials = context.getCredentials();

		try {
			String apiToken = String.valueOf(credentials.getOrDefault("apiToken",
				credentials.getOrDefault("apiKey", "")));
			Map<String, String> headers = getAuthHeaders(apiToken);

			return switch (resource) {
				case "account" -> executeAccount(context, headers);
				case "profile" -> executeProfile(context, operation, headers);
				case "quote" -> executeQuote(context, operation, headers);
				case "recipient" -> executeRecipient(context, operation, headers);
				case "transfer" -> executeTransfer(context, operation, headers);
				default -> NodeExecutionResult.error("Unknown resource: " + resource);
			};
		} catch (Exception e) {
			return handleError(context, "Wise API error: " + e.getMessage(), e);
		}
	}

	// ========================= Account Operations =========================

	private NodeExecutionResult executeAccount(NodeExecutionContext context,
			Map<String, String> headers) throws Exception {
		String profileId = context.getParameter("profileId", "");
		String url = BASE_URL + "/v4/profiles/" + encode(profileId) + "/balances?types=STANDARD";
		HttpResponse<String> response = get(url, headers);
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		return toArrayResult(response);
	}

	// ========================= Profile Operations =========================

	private NodeExecutionResult executeProfile(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "get": {
				String profileId = context.getParameter("profileIdGet", "");
				String url = BASE_URL + "/v2/profiles/" + encode(profileId);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String url = BASE_URL + "/v2/profiles";
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown profile operation: " + operation);
		}
	}

	// ========================= Quote Operations =========================

	private NodeExecutionResult executeQuote(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		String quoteProfileId = context.getParameter("quoteProfileId", "");

		switch (operation) {
			case "create": {
				String sourceCurrency = context.getParameter("sourceCurrency", "");
				String targetCurrency = context.getParameter("targetCurrency", "");
				Object sourceAmount = context.getParameters().get("sourceAmount");
				Object targetAmount = context.getParameters().get("targetAmount");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", Integer.parseInt(quoteProfileId));
				body.put("sourceCurrency", sourceCurrency);
				body.put("targetCurrency", targetCurrency);
				if (sourceAmount != null) {
					body.put("sourceAmount", toDouble(sourceAmount, 0));
				}
				if (targetAmount != null) {
					body.put("targetAmount", toDouble(targetAmount, 0));
				}

				String url = BASE_URL + "/v3/profiles/" + encode(quoteProfileId) + "/quotes";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "get": {
				String quoteId = context.getParameter("quoteId", "");
				String url = BASE_URL + "/v3/profiles/" + encode(quoteProfileId) + "/quotes/" + encode(quoteId);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown quote operation: " + operation);
		}
	}

	// ========================= Recipient Operations =========================

	private NodeExecutionResult executeRecipient(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String profileId = context.getParameter("recipientProfileId", "");
				String currency = context.getParameter("recipientCurrency", "");
				String type = context.getParameter("recipientType", "email");
				String accountHolderName = context.getParameter("recipientAccountHolderName", "");
				String detailsJson = context.getParameter("recipientDetails", "{}");
				Map<String, Object> details = parseJsonObject(detailsJson);

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("profile", Integer.parseInt(profileId));
				body.put("accountHolderName", accountHolderName);
				body.put("currency", currency);
				body.put("type", type);
				body.put("details", details);

				String url = BASE_URL + "/v1/accounts";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "delete": {
				String recipientId = context.getParameter("recipientId", "");
				String url = BASE_URL + "/v1/accounts/" + encode(recipientId);
				HttpResponse<String> response = delete(url, headers);
				if (response.statusCode() >= 400) {
					return apiError(response);
				}
				return NodeExecutionResult.success(List.of(wrapInJson(Map.of("success", true, "recipientId", recipientId))));
			}
			case "get": {
				String recipientId = context.getParameter("recipientId", "");
				String url = BASE_URL + "/v1/accounts/" + encode(recipientId);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String profileId = context.getParameter("recipientProfileId", "");
				String url = BASE_URL + "/v1/accounts?profile=" + encode(profileId);
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown recipient operation: " + operation);
		}
	}

	// ========================= Transfer Operations =========================

	private NodeExecutionResult executeTransfer(NodeExecutionContext context, String operation,
			Map<String, String> headers) throws Exception {
		switch (operation) {
			case "create": {
				String targetAccountId = context.getParameter("transferTargetAccountId", "");
				String quoteUuid = context.getParameter("transferQuoteUuid", "");
				String reference = context.getParameter("transferReference", "");

				Map<String, Object> body = new LinkedHashMap<>();
				body.put("targetAccount", Integer.parseInt(targetAccountId));
				body.put("quoteUuid", quoteUuid);
				if (reference != null && !reference.isEmpty()) {
					Map<String, Object> details = new LinkedHashMap<>();
					details.put("reference", reference);
					body.put("details", details);
				}

				String url = BASE_URL + "/v1/transfers";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "execute": {
				String transferId = context.getParameter("transferIdExecute", "");
				Map<String, Object> body = Map.of("type", "BALANCE");

				String url = BASE_URL + "/v3/profiles/{profileId}/transfers/" + encode(transferId) + "/payments";
				HttpResponse<String> response = post(url, body, headers);
				return toResult(response);
			}
			case "get": {
				String transferId = context.getParameter("transferId", "");
				String url = BASE_URL + "/v1/transfers/" + encode(transferId);
				HttpResponse<String> response = get(url, headers);
				return toResult(response);
			}
			case "getAll": {
				String profileId = context.getParameter("transferProfileId", "");
				Map<String, Object> filters = context.getParameter("transferFilters", Map.of());
				Map<String, Object> queryParams = new LinkedHashMap<>();
				queryParams.put("profile", profileId);
				if (filters.get("limit") != null) queryParams.put("limit", filters.get("limit"));
				if (filters.get("offset") != null) queryParams.put("offset", filters.get("offset"));
				if (filters.get("status") != null && !String.valueOf(filters.get("status")).isEmpty()) {
					queryParams.put("status", filters.get("status"));
				}

				String url = buildUrl(BASE_URL + "/v1/transfers", queryParams);
				HttpResponse<String> response = get(url, headers);
				return toArrayResult(response);
			}
			default:
				return NodeExecutionResult.error("Unknown transfer operation: " + operation);
		}
	}

	// ========================= Helpers =========================

	private Map<String, String> getAuthHeaders(String apiToken) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer " + apiToken);
		headers.put("Content-Type", "application/json");
		headers.put("Accept", "application/json");
		return headers;
	}

	private NodeExecutionResult toResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		String body = response.body();
		if (body == null || body.isBlank()) {
			return NodeExecutionResult.success(List.of(wrapInJson(Map.of("statusCode", response.statusCode()))));
		}
		Map<String, Object> parsed = parseResponse(response);
		return NodeExecutionResult.success(List.of(wrapInJson(parsed)));
	}

	private NodeExecutionResult toArrayResult(HttpResponse<String> response) throws Exception {
		if (response.statusCode() >= 400) {
			return apiError(response);
		}
		List<Map<String, Object>> items = parseArrayResponse(response);
		if (items.isEmpty()) {
			return NodeExecutionResult.empty();
		}
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> item : items) {
			results.add(wrapInJson(item));
		}
		return NodeExecutionResult.success(results);
	}

	private NodeExecutionResult apiError(HttpResponse<String> response) {
		String body = response.body() != null ? response.body() : "";
		if (body.length() > 300) body = body.substring(0, 300) + "...";
		return NodeExecutionResult.error("Wise API error (HTTP " + response.statusCode() + "): " + body);
	}
}
