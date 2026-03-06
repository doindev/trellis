package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ManualTriggerNodeTest {

    private ManualTriggerNode node;

    @BeforeEach
    void setUp() {
        node = new ManualTriggerNode();
    }

    // -- Produces a single trigger item --

    @Test
    void executeProducesSingleTriggerItem() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(output(result)).hasSize(1);
    }

    // -- Trigger item contains executionMode field --

    @Test
    void triggerItemContainsExecutionModeField() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(firstJson(result)).containsEntry("executionMode", "manual");
    }

    // -- Trigger item contains timestamp field --

    @Test
    void triggerItemContainsTimestampField() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(firstJson(result)).containsKey("timestamp");
        assertThat(firstJson(result).get("timestamp")).isNotNull();
        assertThat(firstJson(result).get("timestamp").toString()).isNotEmpty();
    }

    // -- Trigger item contains _triggerTimestamp from base class --

    @Test
    void triggerItemContainsTriggerTimestamp() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
        assertThat(firstJson(result).get("_triggerTimestamp")).isInstanceOf(Long.class);
    }

    // -- No input required --

    @Test
    void executesWithNoInputData() {
        NodeExecutionResult result = node.execute(ctx(List.of(), Map.of()));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("executionMode", "manual");
    }

    @Test
    void executesWithNullInputData() {
        NodeExecutionResult result = node.execute(ctx(null, Map.of()));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("executionMode", "manual");
    }

    // -- No inputs defined (trigger node) --

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // -- Has one output --

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }

    // -- Parameters contain notice --

    @Test
    void parametersContainNotice() {
        assertThat(node.getParameters()).hasSize(1);
        assertThat(node.getParameters().get(0).getName()).isEqualTo("notice");
    }

    // -- Timestamp is a valid ISO instant --

    @Test
    void timestampIsValidIsoInstant() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        String timestamp = firstJson(result).get("timestamp").toString();
        // Should not throw - valid ISO instant
        assertThatCode(() -> java.time.Instant.parse(timestamp)).doesNotThrowAnyException();
    }
}
