package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NASA — access various NASA open APIs (APOD, Asteroid Neo, DONKI, etc.).
 * Authentication via API key passed as query parameter.
 */
@Node(
		type = "nasa",
		displayName = "NASA",
		description = "Access NASA open APIs (APOD, Asteroids, etc.)",
		category = "Miscellaneous",
		icon = "rocket",
		credentials = {"nasaApi"},
		searchOnly = true
)
public class NasaNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.nasa.gov";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey", "DEMO_KEY");
			String resource = context.getParameter("resource", "astronomyPictureOfTheDay");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String endpoint;
					String extraParams = "";
					String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

					switch (resource) {
						case "astronomyPictureOfTheDay" -> {
							endpoint = "/planetary/apod";
							String date = context.getParameter("date", "");
							if (!date.isBlank()) extraParams = "&date=" + encode(date);
						}
						case "asteroidNeoFeed" -> {
							endpoint = "/neo/rest/v1/feed";
							String startDate = context.getParameter("startDate", today);
							String endDate = context.getParameter("endDate", today);
							extraParams = "&start_date=" + encode(startDate) + "&end_date=" + encode(endDate);
						}
						case "asteroidNeoLookup" -> {
							String asteroidId = context.getParameter("asteroidId", "");
							endpoint = "/neo/rest/v1/neo/" + encode(asteroidId);
						}
						case "asteroidNeoBrowse" -> {
							endpoint = "/neo/rest/v1/neo/browse";
							int limit = toInt(context.getParameters().get("limit"), 20);
							extraParams = "&size=" + limit;
						}
						default -> endpoint = "/planetary/apod";
					}

					String url = BASE_URL + endpoint + "?api_key=" + encode(apiKey) + extraParams;
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
						.type(ParameterType.OPTIONS).defaultValue("astronomyPictureOfTheDay")
						.options(List.of(
								ParameterOption.builder().name("Astronomy Picture of the Day").value("astronomyPictureOfTheDay").build(),
								ParameterOption.builder().name("Asteroid Neo Feed").value("asteroidNeoFeed").build(),
								ParameterOption.builder().name("Asteroid Neo Lookup").value("asteroidNeoLookup").build(),
								ParameterOption.builder().name("Asteroid Neo Browse").value("asteroidNeoBrowse").build()
						)).build(),
				NodeParameter.builder()
						.name("date").displayName("Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Date in YYYY-MM-DD format (for APOD).").build(),
				NodeParameter.builder()
						.name("asteroidId").displayName("Asteroid ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Asteroid ID for Neo Lookup.").build(),
				NodeParameter.builder()
						.name("startDate").displayName("Start Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("Start date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("endDate").displayName("End Date")
						.type(ParameterType.STRING).defaultValue("")
						.description("End date in YYYY-MM-DD format.").build(),
				NodeParameter.builder()
						.name("limit").displayName("Limit")
						.type(ParameterType.NUMBER).defaultValue(20)
						.description("Max results to return.").build()
		);
	}
}
