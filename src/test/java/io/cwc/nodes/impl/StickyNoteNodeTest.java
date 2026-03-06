package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class StickyNoteNodeTest {

    private StickyNoteNode node;

    @BeforeEach
    void setUp() {
        node = new StickyNoteNode();
    }

    // ── Execute returns empty result ──

    @Test
    void executeReturnsEmptyResult() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(output(result)).isEmpty();
    }

    // ── Has no inputs ──

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // ── Has no outputs ──

    @Test
    void hasNoOutputs() {
        assertThat(node.getOutputs()).isEmpty();
    }

    // ── Result continues execution ──

    @Test
    void resultContinuesExecution() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(result.isContinueExecution()).isTrue();
    }

    // ── No error produced ──

    @Test
    void noErrorProduced() {
        NodeExecutionResult result = node.execute(ctxEmpty());

        assertThat(result.getError()).isNull();
    }

    // ── Has parameters (content, color, width, height) ──

    @Test
    void hasParameters() {
        assertThat(node.getParameters()).isNotEmpty();
        assertThat(node.getParameters()).extracting("name")
                .contains("content", "color", "width", "height");
    }
}
