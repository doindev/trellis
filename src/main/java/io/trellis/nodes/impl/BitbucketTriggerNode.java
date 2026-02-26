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
 * Bitbucket Trigger -- starts a workflow when a Bitbucket webhook event is received.
 * Supports repository, issue, and pull request events.
 */
@Slf4j
@Node(
	type = "bitbucketTrigger",
	displayName = "Bitbucket Trigger",
	description = "Starts the workflow when Bitbucket events occur",
	category = "Development",
	icon = "bitbucket",
	credentials = {"bitbucketApi"},
	trigger = true
)
public class BitbucketTriggerNode extends AbstractTriggerNode {

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
					// Repository events
					ParameterOption.builder().name("Repository - Push").value("repo:push")
						.description("A push to a repository").build(),
					ParameterOption.builder().name("Repository - Fork").value("repo:fork")
						.description("A repository is forked").build(),
					ParameterOption.builder().name("Repository - Updated").value("repo:updated")
						.description("A repository is updated").build(),
					ParameterOption.builder().name("Repository - Commit Comment Created").value("repo:commit_comment_created")
						.description("A comment is created on a commit").build(),
					ParameterOption.builder().name("Repository - Commit Status Created").value("repo:commit_status_created")
						.description("A commit status is created").build(),
					ParameterOption.builder().name("Repository - Commit Status Updated").value("repo:commit_status_updated")
						.description("A commit status is updated").build(),

					// Issue events
					ParameterOption.builder().name("Issue - Created").value("issue:created")
						.description("An issue is created").build(),
					ParameterOption.builder().name("Issue - Updated").value("issue:updated")
						.description("An issue is updated").build(),
					ParameterOption.builder().name("Issue - Comment Created").value("issue:comment_created")
						.description("A comment is created on an issue").build(),

					// Pull request events
					ParameterOption.builder().name("Pull Request - Created").value("pullrequest:created")
						.description("A pull request is created").build(),
					ParameterOption.builder().name("Pull Request - Updated").value("pullrequest:updated")
						.description("A pull request is updated").build(),
					ParameterOption.builder().name("Pull Request - Approved").value("pullrequest:approved")
						.description("A pull request is approved").build(),
					ParameterOption.builder().name("Pull Request - Unapproved").value("pullrequest:unapproved")
						.description("A pull request approval is removed").build(),
					ParameterOption.builder().name("Pull Request - Fulfilled").value("pullrequest:fulfilled")
						.description("A pull request is merged").build(),
					ParameterOption.builder().name("Pull Request - Rejected").value("pullrequest:rejected")
						.description("A pull request is declined").build(),
					ParameterOption.builder().name("Pull Request - Comment Created").value("pullrequest:comment_created")
						.description("A comment is created on a pull request").build(),
					ParameterOption.builder().name("Pull Request - Comment Updated").value("pullrequest:comment_updated")
						.description("A comment is updated on a pull request").build(),
					ParameterOption.builder().name("Pull Request - Comment Deleted").value("pullrequest:comment_deleted")
						.description("A comment is deleted on a pull request").build()
				)).build()
		);
	}

	@Override
	@SuppressWarnings("unchecked")
	public NodeExecutionResult execute(NodeExecutionContext context) {
		try {
			List<String> events = context.getParameter("events", List.of());
			List<Map<String, Object>> inputData = context.getInputData();

			if (inputData == null || inputData.isEmpty()) {
				// No webhook data received yet, return empty
				return NodeExecutionResult.empty();
			}

			List<Map<String, Object>> results = new ArrayList<>();

			for (Map<String, Object> item : inputData) {
				Map<String, Object> data = unwrapJson(item);
				String eventType = String.valueOf(data.getOrDefault("event", ""));

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

			log.debug("Bitbucket trigger: received {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "Bitbucket Trigger error: " + e.getMessage(), e);
		}
	}
}
