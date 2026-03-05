package io.trellis.nodes.impl;

import java.util.*;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * GitLab Trigger -- starts a workflow when a GitLab webhook event is received.
 * Supports push, tag, issue, note, merge request, wiki, pipeline, and build events.
 */
@Slf4j
@Node(
	type = "gitlabTrigger",
	displayName = "GitLab Trigger",
	description = "Starts the workflow when GitLab events occur",
	category = "Development / DevOps",
	icon = "gitlab",
	credentials = {"gitlabApi"},
	trigger = true
)
public class GitLabTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("events")
				.displayName("Events")
				.type(ParameterType.MULTI_OPTIONS)
				.required(true)
				.description("The events to listen to.")
				.options(List.of(
					ParameterOption.builder().name("Push").value("push")
						.description("Triggered when a push is made to the repository").build(),
					ParameterOption.builder().name("Tag Push").value("tag_push")
						.description("Triggered when a tag is created or deleted").build(),
					ParameterOption.builder().name("Issue").value("issue")
						.description("Triggered when an issue is created, updated, or closed").build(),
					ParameterOption.builder().name("Note").value("note")
						.description("Triggered when a comment is made on a commit, merge request, issue, or snippet").build(),
					ParameterOption.builder().name("Merge Request").value("merge_request")
						.description("Triggered when a merge request is created, updated, or merged").build(),
					ParameterOption.builder().name("Wiki Page").value("wiki_page")
						.description("Triggered when a wiki page is created or updated").build(),
					ParameterOption.builder().name("Pipeline").value("pipeline")
						.description("Triggered on pipeline status changes").build(),
					ParameterOption.builder().name("Build").value("build")
						.description("Triggered on build (job) status changes").build()
				)).build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			List<String> events = context.getParameter("events", List.of());
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData == null || inputData.isEmpty()) {
				return NodeExecutionResult.empty();
			}

			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> data = unwrapJson(item);
				String eventType = String.valueOf(data.getOrDefault("event_name",
					data.getOrDefault("object_kind", "")));

				// Filter by selected events if specified
				if (!events.isEmpty() && !events.contains(eventType)) {
					continue;
				}

				Map<String, Object> triggerData = new LinkedHashMap<>(data);
				triggerData.put("_webhookEvent", eventType);
				results.add(createTriggerItem(triggerData));
			}

			if (results.isEmpty()) {
				return NodeExecutionResult.empty();
			}

			log.debug("GitLab trigger: received {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "GitLab Trigger error: " + e.getMessage(), e);
		}
	}
}
