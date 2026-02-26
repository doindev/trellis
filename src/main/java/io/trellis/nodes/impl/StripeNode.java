package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * Stripe — manage charges, customers, invoices, subscriptions, products,
 * prices, payment intents, payouts, refunds, coupons, and tokens
 * using the Stripe REST API.
 *
 * NOTE: Stripe uses form-encoded POST/PUT bodies, not JSON.
 */
@Node(
		type = "stripe",
		displayName = "Stripe",
		description = "Manage payments, customers, and subscriptions in Stripe",
		category = "E-Commerce / Payments",
		icon = "stripe",
		credentials = {"stripeApi"}
)
public class StripeNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.stripe.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String secretKey = (String) credentials.getOrDefault("secretKey", "");

		String resource = context.getParameter("resource", "charge");
		String operation = context.getParameter("operation", "getAll");

		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + secretKey);
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "balance" -> handleBalance(headers, operation);
					case "charge" -> handleCharge(context, headers, operation);
					case "coupon" -> handleCoupon(context, headers, operation);
					case "customer" -> handleCustomer(context, headers, operation);
					case "customerCard" -> handleCustomerCard(context, headers, operation);
					case "invoice" -> handleInvoice(context, headers, operation);
					case "invoiceItem" -> handleInvoiceItem(context, headers, operation);
					case "paymentIntent" -> handlePaymentIntent(context, headers, operation);
					case "payout" -> handlePayout(context, headers, operation);
					case "price" -> handlePrice(context, headers, operation);
					case "product" -> handleProduct(context, headers, operation);
					case "refund" -> handleRefund(context, headers, operation);
					case "subscription" -> handleSubscription(context, headers, operation);
					case "token" -> handleToken(context, headers, operation);
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

	// ---- Balance ----

	private Map<String, Object> handleBalance(Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				HttpResponse<String> response = get(BASE_URL + "/balance", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown balance operation: " + operation);
		};
	}

	// ---- Charge ----

	private Map<String, Object> handleCharge(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				String currency = context.getParameter("currency", "usd");
				params.put("currency", currency);
				String source = context.getParameter("source", "");
				if (!source.isEmpty()) params.put("source", source);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) params.put("customer", customerId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/charges", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String chargeId = context.getParameter("chargeId", "");
				HttpResponse<String> response = get(BASE_URL + "/charges/" + encode(chargeId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/charges?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String chargeId = context.getParameter("chargeId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String metadata = context.getParameter("metadata", "");
				if (!metadata.isEmpty()) params.put("metadata", metadata);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/charges/" + encode(chargeId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown charge operation: " + operation);
		};
	}

	// ---- Coupon ----

	private Map<String, Object> handleCoupon(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String duration = context.getParameter("duration", "once");
				params.put("duration", duration);
				String percentOff = context.getParameter("percentOff", "");
				if (!percentOff.isEmpty()) params.put("percent_off", percentOff);
				String amountOff = context.getParameter("amountOff", "");
				if (!amountOff.isEmpty()) params.put("amount_off", amountOff);
				String currency = context.getParameter("currency", "usd");
				if (!amountOff.isEmpty()) params.put("currency", currency);
				String couponId = context.getParameter("couponId", "");
				if (!couponId.isEmpty()) params.put("id", couponId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/coupons", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String couponId = context.getParameter("couponId", "");
				HttpResponse<String> response = delete(BASE_URL + "/coupons/" + encode(couponId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String couponId = context.getParameter("couponId", "");
				HttpResponse<String> response = get(BASE_URL + "/coupons/" + encode(couponId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/coupons?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown coupon operation: " + operation);
		};
	}

	// ---- Customer ----

	private Map<String, Object> handleCustomer(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) params.put("email", email);
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) params.put("name", name);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String phone = context.getParameter("phone", "");
				if (!phone.isEmpty()) params.put("phone", phone);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/customers", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = delete(BASE_URL + "/customers/" + encode(customerId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String customerId = context.getParameter("customerId", "");
				HttpResponse<String> response = get(BASE_URL + "/customers/" + encode(customerId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/customers?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String customerId = context.getParameter("customerId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String email = context.getParameter("email", "");
				if (!email.isEmpty()) params.put("email", email);
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) params.put("name", name);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/customers/" + encode(customerId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customer operation: " + operation);
		};
	}

	// ---- Customer Card ----

	private Map<String, Object> handleCustomerCard(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		String customerId = context.getParameter("customerId", "");
		return switch (operation) {
			case "add" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String source = context.getParameter("source", "");
				if (!source.isEmpty()) params.put("source", source);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/customers/" + encode(customerId) + "/sources", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = get(BASE_URL + "/customers/" + encode(customerId) + "/sources/" + encode(cardId), headers);
				yield parseResponse(response);
			}
			case "remove" -> {
				String cardId = context.getParameter("cardId", "");
				HttpResponse<String> response = delete(BASE_URL + "/customers/" + encode(customerId) + "/sources/" + encode(cardId), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown customerCard operation: " + operation);
		};
	}

	// ---- Invoice ----

	private Map<String, Object> handleInvoice(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) params.put("customer", customerId);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoices", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = delete(BASE_URL + "/invoices/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			case "finalize" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoices/" + encode(invoiceId) + "/finalize", "", headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				HttpResponse<String> response = get(BASE_URL + "/invoices/" + encode(invoiceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/invoices?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "pay" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoices/" + encode(invoiceId) + "/pay", "", headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoices/" + encode(invoiceId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "void" -> {
				String invoiceId = context.getParameter("invoiceId", "");
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoices/" + encode(invoiceId) + "/void", "", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoice operation: " + operation);
		};
	}

	// ---- Invoice Item ----

	private Map<String, Object> handleInvoiceItem(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) params.put("customer", customerId);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				String currency = context.getParameter("currency", "usd");
				params.put("currency", currency);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String invoiceId = context.getParameter("invoiceId", "");
				if (!invoiceId.isEmpty()) params.put("invoice", invoiceId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/invoiceitems", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String invoiceItemId = context.getParameter("invoiceItemId", "");
				HttpResponse<String> response = delete(BASE_URL + "/invoiceitems/" + encode(invoiceItemId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String invoiceItemId = context.getParameter("invoiceItemId", "");
				HttpResponse<String> response = get(BASE_URL + "/invoiceitems/" + encode(invoiceItemId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/invoiceitems?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown invoiceItem operation: " + operation);
		};
	}

	// ---- Payment Intent ----

	private Map<String, Object> handlePaymentIntent(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "cancel" -> {
				String paymentIntentId = context.getParameter("paymentIntentId", "");
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/payment_intents/" + encode(paymentIntentId) + "/cancel", "", headers);
				yield parseResponse(response);
			}
			case "confirm" -> {
				String paymentIntentId = context.getParameter("paymentIntentId", "");
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/payment_intents/" + encode(paymentIntentId) + "/confirm", "", headers);
				yield parseResponse(response);
			}
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				String currency = context.getParameter("currency", "usd");
				params.put("currency", currency);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) params.put("customer", customerId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/payment_intents", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String paymentIntentId = context.getParameter("paymentIntentId", "");
				HttpResponse<String> response = get(BASE_URL + "/payment_intents/" + encode(paymentIntentId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/payment_intents?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String paymentIntentId = context.getParameter("paymentIntentId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/payment_intents/" + encode(paymentIntentId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown paymentIntent operation: " + operation);
		};
	}

	// ---- Payout ----

	private Map<String, Object> handlePayout(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				String currency = context.getParameter("currency", "usd");
				params.put("currency", currency);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/payouts", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String payoutId = context.getParameter("payoutId", "");
				HttpResponse<String> response = get(BASE_URL + "/payouts/" + encode(payoutId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/payouts?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown payout operation: " + operation);
		};
	}

	// ---- Price ----

	private Map<String, Object> handlePrice(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String unitAmount = context.getParameter("amount", "");
				if (!unitAmount.isEmpty()) params.put("unit_amount", unitAmount);
				String currency = context.getParameter("currency", "usd");
				params.put("currency", currency);
				String stripeProductId = context.getParameter("stripeProductId", "");
				if (!stripeProductId.isEmpty()) params.put("product", stripeProductId);
				String recurring = context.getParameter("recurringInterval", "");
				if (!recurring.isEmpty()) params.put("recurring[interval]", recurring);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/prices", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String priceId = context.getParameter("priceId", "");
				HttpResponse<String> response = get(BASE_URL + "/prices/" + encode(priceId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/prices?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String priceId = context.getParameter("priceId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String active = context.getParameter("active", "");
				if (!active.isEmpty()) params.put("active", active);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/prices/" + encode(priceId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown price operation: " + operation);
		};
	}

	// ---- Product ----

	private Map<String, Object> handleProduct(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) params.put("name", name);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				String active = context.getParameter("active", "true");
				params.put("active", active);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/products", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String stripeProductId = context.getParameter("stripeProductId", "");
				HttpResponse<String> response = delete(BASE_URL + "/products/" + encode(stripeProductId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String stripeProductId = context.getParameter("stripeProductId", "");
				HttpResponse<String> response = get(BASE_URL + "/products/" + encode(stripeProductId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/products?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String stripeProductId = context.getParameter("stripeProductId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String name = context.getParameter("name", "");
				if (!name.isEmpty()) params.put("name", name);
				String description = context.getParameter("description", "");
				if (!description.isEmpty()) params.put("description", description);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/products/" + encode(stripeProductId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown product operation: " + operation);
		};
	}

	// ---- Refund ----

	private Map<String, Object> handleRefund(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String chargeId = context.getParameter("chargeId", "");
				if (!chargeId.isEmpty()) params.put("charge", chargeId);
				String amount = context.getParameter("amount", "");
				if (!amount.isEmpty()) params.put("amount", amount);
				String reason = context.getParameter("reason", "");
				if (!reason.isEmpty()) params.put("reason", reason);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/refunds", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String refundId = context.getParameter("refundId", "");
				HttpResponse<String> response = get(BASE_URL + "/refunds/" + encode(refundId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/refunds?limit=" + limit, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown refund operation: " + operation);
		};
	}

	// ---- Subscription ----

	private Map<String, Object> handleSubscription(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "cancel" -> {
				String subscriptionId = context.getParameter("subscriptionId", "");
				HttpResponse<String> response = delete(BASE_URL + "/subscriptions/" + encode(subscriptionId), headers);
				yield parseResponse(response);
			}
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String customerId = context.getParameter("customerId", "");
				if (!customerId.isEmpty()) params.put("customer", customerId);
				String priceId = context.getParameter("priceId", "");
				if (!priceId.isEmpty()) params.put("items[0][price]", priceId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/subscriptions", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			case "delete" -> {
				String subscriptionId = context.getParameter("subscriptionId", "");
				HttpResponse<String> response = delete(BASE_URL + "/subscriptions/" + encode(subscriptionId), headers);
				yield parseResponse(response);
			}
			case "get" -> {
				String subscriptionId = context.getParameter("subscriptionId", "");
				HttpResponse<String> response = get(BASE_URL + "/subscriptions/" + encode(subscriptionId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 10);
				HttpResponse<String> response = get(BASE_URL + "/subscriptions?limit=" + limit, headers);
				yield parseResponse(response);
			}
			case "update" -> {
				String subscriptionId = context.getParameter("subscriptionId", "");
				Map<String, Object> params = new LinkedHashMap<>();
				String priceId = context.getParameter("priceId", "");
				if (!priceId.isEmpty()) params.put("items[0][price]", priceId);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/subscriptions/" + encode(subscriptionId), buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown subscription operation: " + operation);
		};
	}

	// ---- Token ----

	private Map<String, Object> handleToken(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "create" -> {
				Map<String, Object> params = new LinkedHashMap<>();
				String cardNumber = context.getParameter("cardNumber", "");
				if (!cardNumber.isEmpty()) params.put("card[number]", cardNumber);
				String expMonth = context.getParameter("expMonth", "");
				if (!expMonth.isEmpty()) params.put("card[exp_month]", expMonth);
				String expYear = context.getParameter("expYear", "");
				if (!expYear.isEmpty()) params.put("card[exp_year]", expYear);
				String cvc = context.getParameter("cvc", "");
				if (!cvc.isEmpty()) params.put("card[cvc]", cvc);
				headers.put("Content-Type", "application/x-www-form-urlencoded");
				HttpResponse<String> response = post(BASE_URL + "/tokens", buildFormBody(params), headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown token operation: " + operation);
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
						.type(ParameterType.OPTIONS).defaultValue("charge")
						.options(List.of(
								ParameterOption.builder().name("Balance").value("balance").build(),
								ParameterOption.builder().name("Charge").value("charge").build(),
								ParameterOption.builder().name("Coupon").value("coupon").build(),
								ParameterOption.builder().name("Customer").value("customer").build(),
								ParameterOption.builder().name("Customer Card").value("customerCard").build(),
								ParameterOption.builder().name("Invoice").value("invoice").build(),
								ParameterOption.builder().name("Invoice Item").value("invoiceItem").build(),
								ParameterOption.builder().name("Payment Intent").value("paymentIntent").build(),
								ParameterOption.builder().name("Payout").value("payout").build(),
								ParameterOption.builder().name("Price").value("price").build(),
								ParameterOption.builder().name("Product").value("product").build(),
								ParameterOption.builder().name("Refund").value("refund").build(),
								ParameterOption.builder().name("Subscription").value("subscription").build(),
								ParameterOption.builder().name("Token").value("token").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Add").value("add").build(),
								ParameterOption.builder().name("Cancel").value("cancel").build(),
								ParameterOption.builder().name("Confirm").value("confirm").build(),
								ParameterOption.builder().name("Create").value("create").build(),
								ParameterOption.builder().name("Delete").value("delete").build(),
								ParameterOption.builder().name("Finalize").value("finalize").build(),
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Pay").value("pay").build(),
								ParameterOption.builder().name("Remove").value("remove").build(),
								ParameterOption.builder().name("Update").value("update").build(),
								ParameterOption.builder().name("Void").value("void").build()
						)).build(),
				NodeParameter.builder()
						.name("chargeId").displayName("Charge ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("couponId").displayName("Coupon ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("customerId").displayName("Customer ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("cardId").displayName("Card ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceId").displayName("Invoice ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("invoiceItemId").displayName("Invoice Item ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("paymentIntentId").displayName("Payment Intent ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("payoutId").displayName("Payout ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("priceId").displayName("Price ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("stripeProductId").displayName("Product ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("refundId").displayName("Refund ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("subscriptionId").displayName("Subscription ID")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("amount").displayName("Amount")
						.type(ParameterType.STRING).defaultValue("")
						.description("Amount in smallest currency unit (e.g. cents).").build(),
				NodeParameter.builder()
						.name("currency").displayName("Currency")
						.type(ParameterType.STRING).defaultValue("usd").build(),
				NodeParameter.builder()
						.name("source").displayName("Source / Token")
						.type(ParameterType.STRING).defaultValue("")
						.description("Payment source or token ID.").build(),
				NodeParameter.builder()
						.name("name").displayName("Name")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("email").displayName("Email")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("phone").displayName("Phone")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("description").displayName("Description")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("metadata").displayName("Metadata")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("duration").displayName("Duration")
						.type(ParameterType.OPTIONS).defaultValue("once")
						.options(List.of(
								ParameterOption.builder().name("Once").value("once").build(),
								ParameterOption.builder().name("Repeating").value("repeating").build(),
								ParameterOption.builder().name("Forever").value("forever").build()
						)).build(),
				NodeParameter.builder()
						.name("percentOff").displayName("Percent Off")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("amountOff").displayName("Amount Off")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("recurringInterval").displayName("Recurring Interval")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Day").value("day").build(),
								ParameterOption.builder().name("Week").value("week").build(),
								ParameterOption.builder().name("Month").value("month").build(),
								ParameterOption.builder().name("Year").value("year").build()
						)).build(),
				NodeParameter.builder()
						.name("active").displayName("Active")
						.type(ParameterType.BOOLEAN).defaultValue(true).build(),
				NodeParameter.builder()
						.name("reason").displayName("Reason")
						.type(ParameterType.OPTIONS).defaultValue("")
						.options(List.of(
								ParameterOption.builder().name("Duplicate").value("duplicate").build(),
								ParameterOption.builder().name("Fraudulent").value("fraudulent").build(),
								ParameterOption.builder().name("Requested by Customer").value("requested_by_customer").build()
						)).build(),
				NodeParameter.builder()
						.name("cardNumber").displayName("Card Number")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("expMonth").displayName("Expiration Month")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("expYear").displayName("Expiration Year")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("cvc").displayName("CVC")
						.type(ParameterType.STRING).defaultValue("").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(10)
						.description("Max results to return.").build()
		);
	}
}
