package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class LimitNodeTest {

    private LimitNode node;

    @BeforeEach
    void setUp() {
        node = new LimitNode();
    }

    // -- firstItems tests --

    @Test
    void firstItemsWithMaxItemsLessThanTotal() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3),
                Map.of("id", 4),
                Map.of("id", 5)
        );

        Map<String, Object> params = mutableMap("maxItems", 3, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
        assertThat(jsonAt(result, 0)).containsEntry("id", 1);
        assertThat(jsonAt(result, 1)).containsEntry("id", 2);
        assertThat(jsonAt(result, 2)).containsEntry("id", 3);
    }

    // -- lastItems tests --

    @Test
    void lastItemsWithMaxItemsLessThanTotal() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3),
                Map.of("id", 4),
                Map.of("id", 5)
        );

        Map<String, Object> params = mutableMap("maxItems", 2, "keep", "lastItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("id", 4);
        assertThat(jsonAt(result, 1)).containsEntry("id", 5);
    }

    // -- maxItems >= total --

    @Test
    void maxItemsEqualToTotalReturnsAll() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        Map<String, Object> params = mutableMap("maxItems", 3, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);
    }

    @Test
    void maxItemsGreaterThanTotalReturnsAll() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2)
        );

        Map<String, Object> params = mutableMap("maxItems", 10, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
    }

    // -- maxItems = 1 --

    @Test
    void maxItemsOneFirstItems() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        Map<String, Object> params = mutableMap("maxItems", 1, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("id", 1);
    }

    @Test
    void maxItemsOneLastItems() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        Map<String, Object> params = mutableMap("maxItems", 1, "keep", "lastItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("id", 3);
    }

    // -- empty input --

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap("maxItems", 5, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap("maxItems", 5, "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // -- default parameters --

    @Test
    void defaultParametersKeepFirstOne() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        // No params: defaults are maxItems=1, keep=firstItems
        NodeExecutionResult result = node.execute(ctx(input));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("id", 1);
    }

    @Test
    void maxItemsAsStringParsedCorrectly() {
        List<Map<String, Object>> input = items(
                Map.of("id", 1),
                Map.of("id", 2),
                Map.of("id", 3)
        );

        Map<String, Object> params = mutableMap("maxItems", "2", "keep", "firstItems");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
    }
}
