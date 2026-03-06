package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class MergeNodeTest {

    private MergeNode node;

    @BeforeEach
    void setUp() {
        node = new MergeNode();
    }

    /**
     * Creates an item with an _inputIndex marker indicating which branch it came from.
     * The MergeNode uses this to split items into input1 (index 0) and input2 (index 1).
     */
    private Map<String, Object> itemWithIndex(Map<String, Object> json, int inputIndex) {
        Map<String, Object> wrappedJson = mutableMap();
        wrappedJson.putAll(json);
        wrappedJson.put("_inputIndex", inputIndex);
        Map<String, Object> item = mutableMap();
        item.put("json", wrappedJson);
        return item;
    }

    // -- Append mode --

    @Test
    void appendModeMergesTwoInputs() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));
        input.add(itemWithIndex(Map.of("name", "Bob"), 0));
        input.add(itemWithIndex(Map.of("name", "Charlie"), 1));
        input.add(itemWithIndex(Map.of("name", "Diana"), 1));

        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(input, params));

        // Append: input1 items first, then input2 items
        assertThat(output(result)).hasSize(4);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
        assertThat(jsonAt(result, 2)).containsEntry("name", "Charlie");
        assertThat(jsonAt(result, 3)).containsEntry("name", "Diana");
    }

    @Test
    void appendModeWithOnlyInput1() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));
        input.add(itemWithIndex(Map.of("name", "Bob"), 0));

        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
    }

    @Test
    void appendModeWithOnlyInput2() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Charlie"), 1));

        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Charlie");
    }

    // -- Choose branch mode --

    @Test
    void chooseBranchSelectsInput1() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));
        input.add(itemWithIndex(Map.of("name", "Bob"), 0));
        input.add(itemWithIndex(Map.of("name", "Charlie"), 1));

        Map<String, Object> params = mutableMap("mode", "chooseBranch", "chooseBranchValue", "input1");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Bob");
    }

    @Test
    void chooseBranchSelectsInput2() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));
        input.add(itemWithIndex(Map.of("name", "Charlie"), 1));
        input.add(itemWithIndex(Map.of("name", "Diana"), 1));

        Map<String, Object> params = mutableMap("mode", "chooseBranch", "chooseBranchValue", "input2");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Charlie");
        assertThat(jsonAt(result, 1)).containsEntry("name", "Diana");
    }

    @Test
    void chooseBranchDefaultsToInput1() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));
        input.add(itemWithIndex(Map.of("name", "Charlie"), 1));

        // No chooseBranchValue param, defaults to "input1"
        Map<String, Object> params = mutableMap("mode", "chooseBranch");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(jsonAt(result, 0)).containsEntry("name", "Alice");
    }

    // -- Empty input --

    @Test
    void emptyInputAppendMode() {
        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputAppendMode() {
        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void emptyInputChooseBranchMode() {
        Map<String, Object> params = mutableMap("mode", "chooseBranch", "chooseBranchValue", "input1");
        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    // -- Items without _inputIndex default to input1 (index 0) --

    @Test
    void itemsWithoutInputIndexDefaultToInput1() {
        // Regular items created via items() helper don't have _inputIndex
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );

        Map<String, Object> params = mutableMap("mode", "chooseBranch", "chooseBranchValue", "input1");
        NodeExecutionResult result = node.execute(ctx(input, params));

        // Without _inputIndex, all items go to input1
        assertThat(output(result)).hasSize(2);
    }

    @Test
    void chooseBranchInput2WithNoInput2Items() {
        // All items have inputIndex=0 (input1)
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("name", "Alice"), 0));

        Map<String, Object> params = mutableMap("mode", "chooseBranch", "chooseBranchValue", "input2");
        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).isEmpty();
    }

    // -- Append mode ordering: input1 items then input2 items --

    @Test
    void appendModePreservesOrderWithinBranches() {
        // Deliberately interleave input indices to verify correct grouping
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(itemWithIndex(Map.of("order", 1), 1));
        input.add(itemWithIndex(Map.of("order", 2), 0));
        input.add(itemWithIndex(Map.of("order", 3), 0));
        input.add(itemWithIndex(Map.of("order", 4), 1));

        Map<String, Object> params = mutableMap("mode", "append", "numberInputs", 2);
        NodeExecutionResult result = node.execute(ctx(input, params));

        // Input1 items (index 0) come first: order=2, order=3
        // Input2 items (index 1) come second: order=1, order=4
        assertThat(output(result)).hasSize(4);
        assertThat(jsonAt(result, 0)).containsEntry("order", 2);
        assertThat(jsonAt(result, 1)).containsEntry("order", 3);
        assertThat(jsonAt(result, 2)).containsEntry("order", 1);
        assertThat(jsonAt(result, 3)).containsEntry("order", 4);
    }
}
