package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.net.http.HttpResponse;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * Figma Trigger — receive webhook events from Figma.
 */
@Slf4j
@Node(
		type = "figmaTrigger",
		displayName = "Figma Trigger",
		description = "Starts the workflow when Figma events occur",
		category = "Miscellaneous",
		icon = "figma",
		credentials = {"figmaApi"},
		trigger = true
)
public class FigmaTriggerNode extends AbstractApiNode {

	private static final String BASE_URL = "https://api.figma.com/v2";

	@Override
	public List<NodeInput> getInputs() {
		return List.of();
	}

	@Override
	public List<NodeOutput> getOutputs() {
		return List.of(NodeOutput.builder().name("main").displayName("Main Output").build());
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("teamId").displayName("Team ID")
						.type(ParameterType.STRING).required(true).defaultValue("")
						.description("The ID of the Figma team to watch.").build(),
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).required(true).defaultValue("FILE_UPDATE")
						.options(List.of(
								ParameterOption.builder().name("File Update").value("FILE_UPDATE")
										.description("Triggered when a file is saved or deleted").build(),
								ParameterOption.builder().name("File Version Update").value("FILE_VERSION_UPDATE")
										.description("Triggered when a named version is created").build(),
								ParameterOption.builder().name("File Comment").value("FILE_COMMENT")
										.description("Triggered when a comment is added").build(),
								ParameterOption.builder().name("Library Publish").value("LIBRARY_PUBLISH")
										.description("Triggered when a library file is published").build()
						)).build(),
				NodeParameter.builder()
						.name("webhookUrl").displayName("Webhook URL")
						.type(ParameterType.STRING).defaultValue("")
						.description("The URL where Figma will send webhook events.").build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		Map<String, Object> credentials = context.getCredentials();
		String accessToken = String.valueOf(credentials.getOrDefault("accessToken", ""));

		String teamId = context.getParameter("teamId", "");
		String event = context.getParameter("event", "FILE_UPDATE");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Authorization", "Bearer " + accessToken);

		List<Map<String, Object>> inputData = context.getInputData();
		List<Map<String, Object>> results = new ArrayList<>();

		for (@SuppressWarnings("unused") Map<String, Object> item : inputData) {
			try {
				// List existing webhooks for the team
				HttpResponse<String> listResp = get(BASE_URL + "/teams/" + encode(teamId) + "/webhooks", headers);
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("teamId", teamId);
				result.put("event", event);
				result.put("webhooks", parseResponse(listResp));
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
}
