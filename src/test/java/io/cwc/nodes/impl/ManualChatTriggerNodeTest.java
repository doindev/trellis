package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ManualChatTriggerNodeTest {

    private ManualChatTriggerNode node;

    @BeforeEach
    void setUp() {
        node = new ManualChatTriggerNode();
    }

    // ── Execute with chatInput produces item with chatInput field ──

    @Test
    void executeWithChatInputProducesItemWithChatInputField() {
        Map<String, Object> params = mutableMap(
                "chatInput", "Hello, world!",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("chatInput", "Hello, world!");
    }

    // ── Default sessionId is "default" ──

    @Test
    void defaultSessionIdIsDefault() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test message"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("sessionId", "default");
    }

    // ── Custom sessionId is preserved ──

    @Test
    void customSessionIdIsPreserved() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test message",
                "sessionId", "session-abc-123"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("sessionId", "session-abc-123");
    }

    // ── Result has executionMode "manual_chat" ──

    @Test
    void resultHasExecutionModeManualChat() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("executionMode", "manual_chat");
    }

    // ── Result has a timestamp ──

    @Test
    void resultHasTimestamp() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("timestamp");
        assertThat(firstJson(result).get("timestamp")).isNotNull();
        assertThat(firstJson(result).get("timestamp").toString()).isNotEmpty();
    }

    // ── Result has _triggerTimestamp from AbstractTriggerNode ──

    @Test
    void resultHasTriggerTimestamp() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
        assertThat(firstJson(result).get("_triggerTimestamp")).isInstanceOf(Long.class);
    }

    // ── Timestamp is a valid ISO instant ──

    @Test
    void timestampIsValidIsoInstant() {
        Map<String, Object> params = mutableMap(
                "chatInput", "test",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        String timestamp = firstJson(result).get("timestamp").toString();
        assertThatCode(() -> java.time.Instant.parse(timestamp)).doesNotThrowAnyException();
    }

    // ── Empty chatInput produces item with empty chatInput ──

    @Test
    void emptyChatInputProducesItemWithEmptyChatInput() {
        Map<String, Object> params = mutableMap(
                "chatInput", "",
                "sessionId", "default"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("chatInput", "");
    }

    // ── No chatInput parameter defaults to empty string ──

    @Test
    void noChatInputParameterDefaultsToEmptyString() {
        NodeExecutionResult result = node.execute(ctx(List.of(), Map.of()));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("chatInput", "");
    }

    // ── Node has no inputs (trigger node) ──

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // ── Node has one output ──

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }

    // ── Node has parameters ──

    @Test
    void hasParameters() {
        assertThat(node.getParameters()).isNotEmpty();
        assertThat(node.getParameters().get(0).getName()).isEqualTo("notice");
    }
}
