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
 * GitHub Trigger -- starts a workflow when a GitHub webhook event is received.
 * Supports a comprehensive list of GitHub webhook event types.
 */
@Slf4j
@Node(
	type = "githubTrigger",
	displayName = "GitHub Trigger",
	description = "Starts the workflow when GitHub events occur",
	category = "Development",
	icon = "github",
	credentials = {"githubApi"},
	trigger = true
)
public class GitHubTriggerNode extends AbstractTriggerNode {

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
					ParameterOption.builder().name("Check Run").value("check_run")
						.description("Check run activity has occurred").build(),
					ParameterOption.builder().name("Check Suite").value("check_suite")
						.description("Check suite activity has occurred").build(),
					ParameterOption.builder().name("Commit Comment").value("commit_comment")
						.description("A commit comment is created").build(),
					ParameterOption.builder().name("Create").value("create")
						.description("A branch or tag is created").build(),
					ParameterOption.builder().name("Delete").value("delete")
						.description("A branch or tag is deleted").build(),
					ParameterOption.builder().name("Deployment").value("deployment")
						.description("A deployment is created").build(),
					ParameterOption.builder().name("Deployment Status").value("deployment_status")
						.description("A deployment status is updated").build(),
					ParameterOption.builder().name("Fork").value("fork")
						.description("A repository is forked").build(),
					ParameterOption.builder().name("Gollum").value("gollum")
						.description("A wiki page is created or updated").build(),
					ParameterOption.builder().name("Issue Comment").value("issue_comment")
						.description("Activity related to an issue comment").build(),
					ParameterOption.builder().name("Issues").value("issues")
						.description("Activity related to an issue").build(),
					ParameterOption.builder().name("Label").value("label")
						.description("Activity related to a label").build(),
					ParameterOption.builder().name("Member").value("member")
						.description("Activity related to repository collaborators").build(),
					ParameterOption.builder().name("Milestone").value("milestone")
						.description("Activity related to milestones").build(),
					ParameterOption.builder().name("Page Build").value("page_build")
						.description("Pages site is built or build fails").build(),
					ParameterOption.builder().name("Project").value("project")
						.description("Activity related to project boards").build(),
					ParameterOption.builder().name("Project Card").value("project_card")
						.description("Activity related to project cards").build(),
					ParameterOption.builder().name("Project Column").value("project_column")
						.description("Activity related to project columns").build(),
					ParameterOption.builder().name("Public").value("public")
						.description("A private repository is made public").build(),
					ParameterOption.builder().name("Pull Request").value("pull_request")
						.description("Activity related to pull requests").build(),
					ParameterOption.builder().name("Pull Request Review").value("pull_request_review")
						.description("Activity related to pull request reviews").build(),
					ParameterOption.builder().name("Pull Request Review Comment").value("pull_request_review_comment")
						.description("Activity related to pull request review comments").build(),
					ParameterOption.builder().name("Push").value("push")
						.description("One or more commits are pushed").build(),
					ParameterOption.builder().name("Release").value("release")
						.description("Activity related to releases").build(),
					ParameterOption.builder().name("Repository").value("repository")
						.description("Activity related to a repository").build(),
					ParameterOption.builder().name("Star").value("star")
						.description("Activity related to repository stars").build(),
					ParameterOption.builder().name("Status").value("status")
						.description("The status of a Git commit changes").build(),
					ParameterOption.builder().name("Team Add").value("team_add")
						.description("A team is added to a repository").build(),
					ParameterOption.builder().name("Watch").value("watch")
						.description("A user stars a repository").build()
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

			log.debug("GitHub trigger: received {} events", results.size());
			return NodeExecutionResult.success(results);
		} catch (Exception e) {
			return handleError(context, "GitHub Trigger error: " + e.getMessage(), e);
		}
	}
}
