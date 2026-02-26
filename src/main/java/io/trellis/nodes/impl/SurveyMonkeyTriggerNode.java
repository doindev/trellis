package io.trellis.nodes.impl;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractApiNode;
import io.trellis.nodes.core.*;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;

import java.util.*;

/**
 * SurveyMonkey Trigger — receive webhook events from SurveyMonkey surveys.
 */
@Node(
		type = "surveyMonkeyTrigger",
		displayName = "SurveyMonkey Trigger",
		description = "Triggers when a SurveyMonkey event occurs",
		category = "Surveys & Forms",
		icon = "surveyMonkey",
		trigger = true,
		credentials = {"surveyMonkeyApi"}
)
public class SurveyMonkeyTriggerNode extends AbstractApiNode {

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		List<Map<String, Object>> inputData = context.getInputData();
		if (inputData != null && !inputData.isEmpty()) {
			return NodeExecutionResult.success(inputData);
		}

		return NodeExecutionResult.success(List.of(wrapInJson(Map.of(
				"event", context.getParameter("event", "response_completed"),
				"message", "SurveyMonkey trigger activated"
		))));
	}

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
				NodeParameter.builder()
						.name("event").displayName("Event")
						.type(ParameterType.OPTIONS).defaultValue("response_completed")
						.options(List.of(
								ParameterOption.builder().name("Collector Created").value("collector_created").build(),
								ParameterOption.builder().name("Collector Deleted").value("collector_deleted").build(),
								ParameterOption.builder().name("Collector Updated").value("collector_updated").build(),
								ParameterOption.builder().name("Response Completed").value("response_completed").build(),
								ParameterOption.builder().name("Response Created").value("response_created").build(),
								ParameterOption.builder().name("Response Deleted").value("response_deleted").build(),
								ParameterOption.builder().name("Response Disqualified").value("response_disqualified").build(),
								ParameterOption.builder().name("Response Updated").value("response_updated").build(),
								ParameterOption.builder().name("Survey Created").value("survey_created").build(),
								ParameterOption.builder().name("Survey Deleted").value("survey_deleted").build(),
								ParameterOption.builder().name("Survey Updated").value("survey_updated").build()
						)).build(),
				NodeParameter.builder()
						.name("surveyId").displayName("Survey ID")
						.type(ParameterType.STRING).defaultValue("")
						.description("Filter to a specific survey.").build()
		);
	}
}
