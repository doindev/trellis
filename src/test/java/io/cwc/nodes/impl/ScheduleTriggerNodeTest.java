package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ScheduleTriggerNodeTest {

    private ScheduleTriggerNode node;

    @BeforeEach
    void setUp() {
        node = new ScheduleTriggerNode();
    }

    // -- Interval rule mode --

    @Test
    void intervalRuleProducesIntervalField() {
        Map<String, Object> params = mutableMap(
                "rule", "interval",
                "interval", 120,
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("rule", "interval");
        assertThat(firstJson(result)).containsEntry("interval", 120);
    }

    @Test
    void intervalRuleContainsTimestamp() {
        Map<String, Object> params = mutableMap(
                "rule", "interval",
                "interval", 60,
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("timestamp");
        assertThat(firstJson(result).get("timestamp").toString()).isNotEmpty();
    }

    @Test
    void intervalRuleDoesNotContainCronExpression() {
        Map<String, Object> params = mutableMap(
                "rule", "interval",
                "interval", 30,
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).doesNotContainKey("cronExpression");
    }

    // -- Cron expression rule mode --

    @Test
    void cronExpressionRuleProducesCronField() {
        Map<String, Object> params = mutableMap(
                "rule", "cronExpression",
                "cronExpression", "*/5 * * * *",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("rule", "cronExpression");
        assertThat(firstJson(result)).containsEntry("cronExpression", "*/5 * * * *");
    }

    @Test
    void cronExpressionRuleDoesNotContainInterval() {
        Map<String, Object> params = mutableMap(
                "rule", "cronExpression",
                "cronExpression", "0 0 * * *",
                "timezone", "UTC"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).doesNotContainKey("interval");
    }

    // -- Default parameters --

    @Test
    void defaultParametersUseIntervalRuleAndUtcTimezone() {
        // No explicit params, defaults are used: rule=interval, interval=60, timezone=UTC
        NodeExecutionResult result = node.execute(ctx(List.of(), Map.of()));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("rule", "interval");
        assertThat(firstJson(result)).containsEntry("interval", 60);
        assertThat(firstJson(result)).containsEntry("timezone", "UTC");
    }

    @Test
    void defaultCronExpressionIsUsedWhenCronRuleWithNoExpression() {
        Map<String, Object> params = mutableMap("rule", "cronExpression");

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("cronExpression", "0 0 * * *");
    }

    // -- Timezone parameter --

    @Test
    void customTimezoneIsIncludedInOutput() {
        Map<String, Object> params = mutableMap(
                "rule", "interval",
                "interval", 60,
                "timezone", "America/New_York"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("timezone", "America/New_York");
    }

    @Test
    void timezoneDefaultsToUtcWhenNotSpecified() {
        Map<String, Object> params = mutableMap(
                "rule", "interval",
                "interval", 60
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("timezone", "UTC");
    }

    // -- Trigger timestamp injected by base class --

    @Test
    void triggerTimestampIsInjected() {
        Map<String, Object> params = mutableMap("rule", "interval", "interval", 60);

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
    }

    // -- No inputs (trigger node) --

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // -- Has one output --

    @Test
    void hasOneMainOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }

    // -- Parameters --

    @Test
    void hasFourParameters() {
        assertThat(node.getParameters()).hasSize(4);
    }
}
