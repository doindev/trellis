package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class NoOpNodeTest {

    private NoOpNode node;

    @BeforeEach
    void setUp() {
        node = new NoOpNode();
    }

    @Test
    void normalInputDataPassesThrough() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "age", 30)
        );

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("name", "Alice");
        assertThat(firstJson(result)).containsEntry("age", 30);
    }

    @Test
    void nullInputReturnsEmpty() {
        NodeExecutionResult result = node.execute(ctx(null));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void emptyInputReturnsEmpty() {
        NodeExecutionResult result = node.execute(ctx(List.of()));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void multipleItemsPassThrough() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1, "value", "first"),
                Map.of("id", 2, "value", "second"),
                Map.of("id", 3, "value", "third")
        );

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 1)).containsEntry("id", 2);
        assertThat(jsonAt(result, 2)).containsEntry("id", 3);
    }

    @Test
    void singleFieldItemPassesThrough() {
        List<Map<String, Object>> input = List.of(item("key", "value"));

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("key", "value");
    }

    @Test
    void resultContinuesExecution() {
        List<Map<String, Object>> input = items(Map.of("x", 1));

        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(result.isContinueExecution()).isTrue();
        assertThat(result.getError()).isNull();
    }
}
