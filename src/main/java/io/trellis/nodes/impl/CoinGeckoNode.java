package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

/**
 * CoinGecko — retrieve cryptocurrency data from the CoinGecko API.
 */
@Node(
		type = "coinGecko",
		displayName = "CoinGecko",
		description = "Get cryptocurrency data from CoinGecko",
		category = "Miscellaneous",
		icon = "coinGecko",
		credentials = {}
)
public class CoinGeckoNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.coingecko.com/api/v3";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String resource = context.getParameter("resource", "coin");
		String operation = context.getParameter("operation", "get");

		Map<String, String> headers = new HashMap<>();
		headers.put("Accept", "application/json");

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				Map<String, Object> result = switch (resource) {
					case "coin" -> handleCoin(context, headers, operation);
					case "event" -> handleEvent(context, headers, operation);
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

	private Map<String, Object> handleCoin(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "get" -> {
				String coinId = context.getParameter("coinId", "bitcoin");
				HttpResponse<String> response = get(BASE_URL + "/coins/" + encode(coinId), headers);
				yield parseResponse(response);
			}
			case "getAll" -> {
				int limit = toInt(context.getParameters().get("limit"), 100);
				String currency = context.getParameter("currency", "usd");
				String order = context.getParameter("order", "market_cap_desc");
				String url = BASE_URL + "/coins/markets?vs_currency=" + encode(currency)
						+ "&order=" + encode(order) + "&per_page=" + limit + "&page=1";
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "market" -> {
				String coinId = context.getParameter("coinId", "bitcoin");
				String currency = context.getParameter("currency", "usd");
				String days = context.getParameter("days", "30");
				String url = BASE_URL + "/coins/" + encode(coinId)
						+ "/market_chart?vs_currency=" + encode(currency) + "&days=" + encode(days);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			case "ticker" -> {
				String coinId = context.getParameter("coinId", "bitcoin");
				HttpResponse<String> response = get(BASE_URL + "/coins/" + encode(coinId) + "/tickers", headers);
				yield parseResponse(response);
			}
			case "price" -> {
				String coinIds = context.getParameter("coinIds", "bitcoin");
				String currencies = context.getParameter("currencies", "usd");
				String url = BASE_URL + "/simple/price?ids=" + encode(coinIds) + "&vs_currencies=" + encode(currencies);
				HttpResponse<String> response = get(url, headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown coin operation: " + operation);
		};
	}

	private Map<String, Object> handleEvent(NodeExecutionContext context, Map<String, String> headers, String operation) throws Exception {
		return switch (operation) {
			case "getAll" -> {
				HttpResponse<String> response = get(BASE_URL + "/events", headers);
				yield parseResponse(response);
			}
			default -> throw new IllegalArgumentException("Unknown event operation: " + operation);
		};
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("coin")
						.options(List.of(
								ParameterOption.builder().name("Coin").value("coin").build(),
								ParameterOption.builder().name("Event").value("event").build()
						)).build(),
				NodeParameter.builder()
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("getAll")
						.options(List.of(
								ParameterOption.builder().name("Get").value("get").build(),
								ParameterOption.builder().name("Get All").value("getAll").build(),
								ParameterOption.builder().name("Market Chart").value("market").build(),
								ParameterOption.builder().name("Price").value("price").build(),
								ParameterOption.builder().name("Tickers").value("ticker").build()
						)).build(),
				NodeParameter.builder()
						.name("coinId").displayName("Coin ID")
						.type(ParameterType.STRING).defaultValue("bitcoin")
						.description("CoinGecko coin ID (e.g., bitcoin, ethereum).").build(),
				NodeParameter.builder()
						.name("coinIds").displayName("Coin IDs")
						.type(ParameterType.STRING).defaultValue("bitcoin")
						.description("Comma-separated coin IDs for price lookup.").build(),
				NodeParameter.builder()
						.name("currency").displayName("Currency")
						.type(ParameterType.STRING).defaultValue("usd")
						.description("Target currency (e.g., usd, eur, btc).").build(),
				NodeParameter.builder()
						.name("currencies").displayName("Currencies")
						.type(ParameterType.STRING).defaultValue("usd")
						.description("Comma-separated target currencies.").build(),
				NodeParameter.builder()
						.name("order").displayName("Order")
						.type(ParameterType.OPTIONS).defaultValue("market_cap_desc")
						.options(List.of(
								ParameterOption.builder().name("Market Cap Descending").value("market_cap_desc").build(),
								ParameterOption.builder().name("Market Cap Ascending").value("market_cap_asc").build(),
								ParameterOption.builder().name("Volume Descending").value("volume_desc").build(),
								ParameterOption.builder().name("Volume Ascending").value("volume_asc").build()
						)).build(),
				NodeParameter.builder()
						.name("days").displayName("Days")
						.type(ParameterType.STRING).defaultValue("30")
						.description("Number of days for market chart (1, 7, 14, 30, 90, 180, 365, max).").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(100)
						.description("Max coins to return.").build()
		);
	}
}
