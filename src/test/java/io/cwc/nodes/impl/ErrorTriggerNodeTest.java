package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ErrorTriggerNodeTest {

    private ErrorTriggerNode node;

    @BeforeEach
    void setUp() {
        node = new ErrorTriggerNode();
    }

    // -- Basic trigger without filter (with error data in input) --

    @Test
    void triggersWithErrorDataAndNoFilter() {
        List<Map<String, Object>> input = items(
                mutableMap(
                        "workflowId", "wf-123",
                        "executionId", "exec-456",
                        "errorMessage", "Something went wrong"
                )
        );
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("workflowId", "wf-123");
        assertThat(firstJson(result)).containsEntry("executionId", "exec-456");
        assertThat(firstJson(result)).containsEntry("errorMessage", "Something went wrong");
    }

    @Test
    void triggerItemContainsTriggerTimestampWhenInputProvided() {
        List<Map<String, Object>> input = items(
                mutableMap("workflowId", "wf-1", "errorMessage", "fail")
        );
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
    }

    // -- With targetWorkflowId filter (matching) --

    @Test
    void triggersWhenTargetWorkflowIdMatches() {
        List<Map<String, Object>> input = items(
                mutableMap("workflowId", "wf-target", "error", "timeout")
        );
        Map<String, Object> params = mutableMap("targetWorkflowId", "wf-target");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("workflowId", "wf-target");
        assertThat(firstJson(result)).containsEntry("error", "timeout");
    }

    // -- With targetWorkflowId filter (non-matching) --

    @Test
    void returnsEmptyWhenTargetWorkflowIdDoesNotMatch() {
        List<Map<String, Object>> input = items(
                mutableMap("workflowId", "wf-other", "error", "timeout")
        );
        Map<String, Object> params = mutableMap("targetWorkflowId", "wf-target");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).isEmpty();
    }

    // -- Manual execution (no input data) --

    @Test
    void manualExecutionProducesPlaceholderErrorItem() {
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("error");
        assertThat(firstJson(result)).containsKey("workflowId");
        assertThat(firstJson(result)).containsKey("executionId");
    }

    @Test
    void manualExecutionUsesContextWorkflowAndExecutionIds() {
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        // contextBuilder() defaults: workflowId=wf-test, executionId=exec-test
        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("workflowId", "wf-test");
        assertThat(firstJson(result)).containsEntry("executionId", "exec-test");
    }

    // -- Null input data --

    @Test
    void nullInputDataProducesPlaceholderItem() {
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsKey("error");
    }

    // -- No inputs defined (trigger node) --

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // -- Has one output --

    @Test
    void hasOneMainOutput() {
        assertThat(node.getOutputs()).hasSize(1);
    }

    // -- Parameter definition --

    @Test
    void hasTargetWorkflowIdParameter() {
        assertThat(node.getParameters()).hasSize(1);
        assertThat(node.getParameters().get(0).getName()).isEqualTo("targetWorkflowId");
    }

    // -- Filter with empty string is treated as no filter --

    @Test
    void emptyStringTargetWorkflowIdTreatedAsNoFilter() {
        List<Map<String, Object>> input = items(
                mutableMap("workflowId", "any-workflow", "error", "some error")
        );
        Map<String, Object> params = mutableMap("targetWorkflowId", "");

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("workflowId", "any-workflow");
    }
}
