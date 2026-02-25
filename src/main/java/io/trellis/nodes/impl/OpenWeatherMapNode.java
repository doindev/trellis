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
 * OpenWeatherMap — retrieve weather data via the OpenWeatherMap API.
 */
@Node(
		type = "openWeatherMap",
		displayName = "OpenWeatherMap",
		description = "Get weather data from OpenWeatherMap",
		category = "Miscellaneous",
		icon = "cloud-sun",
		credentials = {"openWeatherMapApi"}
)
public class OpenWeatherMapNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.openweathermap.org/data/2.5";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String apiKey = context.getCredentialString("apiKey");
			String operation = context.getParameter("operation", "currentWeather");
			String locationSelection = context.getParameter("locationSelection", "cityName");
			String cityName = context.getParameter("cityName", "");
			double latitude = toDouble(context.getParameters().get("latitude"), 0.0);
			double longitude = toDouble(context.getParameters().get("longitude"), 0.0);
			String units = context.getParameter("units", "metric");
			String language = context.getParameter("language", "en");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String endpoint = "fiveDayForecast".equals(operation) ? "/forecast" : "/weather";
					String url = BASE_URL + endpoint + "?appid=" + encode(apiKey)
							+ "&units=" + encode(units) + "&lang=" + encode(language);

					if ("coordinates".equals(locationSelection)) {
						url += "&lat=" + latitude + "&lon=" + longitude;
					} else {
						url += "&q=" + encode(cityName);
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
						.name("operation").displayName("Operation")
						.type(ParameterType.OPTIONS).defaultValue("currentWeather")
						.options(List.of(
								ParameterOption.builder().name("Current Weather").value("currentWeather").build(),
								ParameterOption.builder().name("5-Day Forecast").value("fiveDayForecast").build()
						)).build(),
				NodeParameter.builder()
						.name("locationSelection").displayName("Location Selection")
						.type(ParameterType.OPTIONS).defaultValue("cityName")
						.options(List.of(
								ParameterOption.builder().name("City Name").value("cityName").build(),
								ParameterOption.builder().name("Coordinates").value("coordinates").build()
						)).build(),
				NodeParameter.builder()
						.name("cityName").displayName("City Name")
						.type(ParameterType.STRING).defaultValue("")
						.description("City name, e.g. 'London' or 'London,GB'.").build(),
				NodeParameter.builder()
						.name("latitude").displayName("Latitude")
						.type(ParameterType.NUMBER).defaultValue(0.0).build(),
				NodeParameter.builder()
						.name("longitude").displayName("Longitude")
						.type(ParameterType.NUMBER).defaultValue(0.0).build(),
				NodeParameter.builder()
						.name("units").displayName("Units")
						.type(ParameterType.OPTIONS).defaultValue("metric")
						.options(List.of(
								ParameterOption.builder().name("Metric").value("metric").build(),
								ParameterOption.builder().name("Imperial").value("imperial").build(),
								ParameterOption.builder().name("Standard").value("standard").build()
						)).build(),
				NodeParameter.builder()
						.name("language").displayName("Language")
						.type(ParameterType.STRING).defaultValue("en")
						.description("Language code (e.g. en, de, fr).").build()
		);
	}
}
