package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Marketstack — retrieve stock market data (end-of-day, tickers, exchanges)
 * via the Marketstack API.
 */
@Node(
		type = "marketstack",
		displayName = "Marketstack",
		description = "Retrieve stock market data via Marketstack",
		category = "Miscellaneous",
		icon = "chart-line",
		credentials = {"marketstackApi"},
		searchOnly = true
)
public class MarketstackNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.marketstack.com/v1";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String resource = context.getParameter("resource", "endOfDayData");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String url;
					switch (resource) {
						case "endOfDayData" -> {
							String symbols = context.getParameter("symbols", "");
							int limit = toInt(context.getParameters().get("limit"), 50);
							url = BASE_URL + "/eod?access_key=" + encode(apiKey)
									+ "&symbols=" + encode(symbols) + "&limit=" + limit;
						}
						case "exchange" -> {
							String exchange = context.getParameter("exchange", "");
							url = BASE_URL + "/exchanges/" + encode(exchange)
									+ "?access_key=" + encode(apiKey);
						}
						case "ticker" -> {
							String symbol = context.getParameter("symbol", "");
							url = BASE_URL + "/tickers/" + encode(symbol)
									+ "?access_key=" + encode(apiKey);
						}
						default -> url = BASE_URL + "/eod?access_key=" + encode(apiKey);
					}

					var response = get(url, Map.of());
					results.add(wrapInJson(parseResponse(response)));
				} catch (Exception e) {
					if (context.isContinueOnFail()) {
						results.add(wrapInJson(Map.of("error", e.getMessage())));
					} else {
						return handleError(context, e.getMessage(), e);
					}
				}
			}
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, e.getMessage(), e);
		}
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("resource").displayName("Resource")
						.type(ParameterType.OPTIONS).defaultValue("endOfDayData")
						.options(List.of(
								ParameterOption.builder().name("End-of-Day Data").value("endOfDayData").build(),
								ParameterOption.builder().name("Exchange").value("exchange").build(),
								ParameterOption.builder().name("Ticker").value("ticker").build()
						)).build(),
				NodeParameter.builder()
						.name("symbols").displayName("Symbols")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated stock symbols, e.g. 'AAPL,MSFT'.").build(),
				NodeParameter.builder()
						.name("symbol").displayName("Symbol")
						.type(ParameterType.STRING).defaultValue("")
						.description("Stock symbol, e.g. 'AAPL'.").build(),
				NodeParameter.builder()
						.name("exchange").displayName("Exchange")
						.type(ParameterType.STRING).defaultValue("")
						.description("Exchange code (MIC), e.g. 'XNAS'.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(50)
						.description("Max results to return.").build()
		);
	}
}
