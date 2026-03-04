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
 * QuickChart — generate chart images via the QuickChart.io API.
 * No authentication required (public API).
 */
@Node(
		type = "quickChart",
		displayName = "QuickChart",
		description = "Generate chart images via QuickChart.io",
		category = "Miscellaneous",
		icon = "chart-bar",
		searchOnly = true
)
public class QuickChartNode extends AbstractApiNode {

	private static final String BASE_URL = "https://quickchart.io/chart";

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			String chartType = context.getParameter("chartType", "bar");
			String labels = context.getParameter("labels", "");
			String data = context.getParameter("data", "");
			String datasetLabel = context.getParameter("datasetLabel", "Dataset");
			int width = toInt(context.getParameters().get("width"), 500);
			int height = toInt(context.getParameters().get("height"), 300);
			String backgroundColor = context.getParameter("backgroundColor", "white");

			List<Map<String, Object>> inputData = context.getInputData();
			List<Map<String, Object>> results = new ArrayList<>();

			for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
				try {
					String chartConfig = String.format(
							"{\"type\":\"%s\",\"data\":{\"labels\":[%s],\"datasets\":[{\"label\":\"%s\",\"data\":[%s]}]}}",
							chartType, labels, datasetLabel, data);

					String url = BASE_URL + "?c=" + encode(chartConfig)
							+ "&w=" + width + "&h=" + height
							+ "&bkg=" + encode(backgroundColor);

					results.add(wrapInJson(Map.of(
							"url", url,
							"chartConfig", chartConfig
					)));
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
						.name("chartType").displayName("Chart Type")
						.type(ParameterType.OPTIONS).defaultValue("bar")
						.options(List.of(
								ParameterOption.builder().name("Bar").value("bar").build(),
								ParameterOption.builder().name("Line").value("line").build(),
								ParameterOption.builder().name("Pie").value("pie").build(),
								ParameterOption.builder().name("Doughnut").value("doughnut").build(),
								ParameterOption.builder().name("Radar").value("radar").build(),
								ParameterOption.builder().name("Polar Area").value("polarArea").build()
						)).build(),
				NodeParameter.builder()
						.name("labels").displayName("Labels")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated labels, e.g. '\"Jan\",\"Feb\",\"Mar\"'.").build(),
				NodeParameter.builder()
						.name("data").displayName("Data")
						.type(ParameterType.STRING).defaultValue("")
						.description("Comma-separated numeric values, e.g. '10,20,30'.").build(),
				NodeParameter.builder()
						.name("datasetLabel").displayName("Dataset Label")
						.type(ParameterType.STRING).defaultValue("Dataset").build(),
				NodeParameter.builder()
						.name("width").displayName("Width")
						.type(ParameterType.NUMBER).defaultValue(500).build(),
				NodeParameter.builder()
						.name("height").displayName("Height")
						.type(ParameterType.NUMBER).defaultValue(300).build(),
				NodeParameter.builder()
						.name("backgroundColor").displayName("Background Color")
						.type(ParameterType.STRING).defaultValue("white").build()
		);
	}
}
