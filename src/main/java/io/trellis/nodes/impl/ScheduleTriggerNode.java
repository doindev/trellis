package io.trellis.nodes.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.trellis.nodes.annotation.Node;
import io.trellis.nodes.base.AbstractTriggerNode;
import io.trellis.nodes.core.NodeExecutionContext;
import io.trellis.nodes.core.NodeExecutionResult;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeParameter.ParameterOption;
import io.trellis.nodes.core.NodeParameter.ParameterType;
import lombok.extern.slf4j.Slf4j;

/**
 * Schedule Trigger Node - triggers workflows on a time-based schedule.
 * Supports both fixed interval and cron expression scheduling.
 */
@Slf4j
@Node(
	type = "scheduleTrigger",
	displayName = "On a schedule",
	description = "Triggers the workflow on a time-based schedule using either a fixed interval or a cron expression.",
	category = "Core Triggers",
	icon = "clock",
	trigger = true
)
public class ScheduleTriggerNode extends AbstractTriggerNode {

	@Override
	public List<NodeParameter> getParameters() {
		return List.of(
			NodeParameter.builder()
				.name("rule")
				.displayName("Trigger Rule")
				.description("How the schedule should be defined.")
				.type(ParameterType.OPTIONS)
				.defaultValue("interval")
				.required(true)
				.options(List.of(
					ParameterOption.builder()
						.name("Interval")
						.value("interval")
						.description("Trigger at a fixed time interval")
						.build(),
					ParameterOption.builder()
						.name("Cron Expression")
						.value("cronExpression")
						.description("Trigger based on a cron expression")
						.build()
				))
				.build(),

			NodeParameter.builder()
				.name("interval")
				.displayName("Interval (Seconds)")
				.description("The interval in seconds between each trigger.")
				.type(ParameterType.NUMBER)
				.defaultValue(60)
				.required(true)
				.minValue(1)
				.displayOptions(Map.of("show", Map.of("rule", List.of("interval"))))
				.build(),

			NodeParameter.builder()
				.name("cronExpression")
				.displayName("Cron Expression")
				.description("The cron expression defining the schedule (e.g., '0 0 * * *' for daily at midnight).")
				.type(ParameterType.STRING)
				.defaultValue("0 0 * * *")
				.required(true)
				.placeHolder("0 0 * * *")
				.displayOptions(Map.of("show", Map.of("rule", List.of("cronExpression"))))
				.build(),

			NodeParameter.builder()
				.name("timezone")
				.displayName("Timezone")
				.description("The timezone for the schedule (e.g., 'America/New_York').")
				.type(ParameterType.STRING)
				.defaultValue("UTC")
				.placeHolder("UTC")
				.build()
		);
	}

	@Override
	public NodeExecutionResult execute(NodeExecutionContext context) {
		String rule = context.getParameter("rule", "interval");
		String timezone = context.getParameter("timezone", "UTC");

		log.debug("Schedule trigger fired for workflow: {}, rule: {}", context.getWorkflowId(), rule);

		Map<String, Object> scheduleInfo = new HashMap<>();
		scheduleInfo.put("timestamp", Instant.now().toString());
		scheduleInfo.put("rule", rule);
		scheduleInfo.put("timezone", timezone);

		if ("interval".equals(rule)) {
			int interval = toInt(context.getParameter("interval", 60), 60);
			scheduleInfo.put("interval", interval);
		} else if ("cronExpression".equals(rule)) {
			String cronExpression = context.getParameter("cronExpression", "0 0 * * *");
			scheduleInfo.put("cronExpression", cronExpression);
		}

		Map<String, Object> triggerItem = createTriggerItem(scheduleInfo);
		return NodeExecutionResult.success(List.of(triggerItem));
	}
}
