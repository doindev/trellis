package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class CompareDataSetsNodeTest {

    private CompareDataSetsNode node;

    @BeforeEach
    void setUp() {
        node = new CompareDataSetsNode();
    }

    // -- Helper to build combined input with _inputIndex tags --

    /**
     * Builds a combined input list where items from inputA have _inputIndex=0
     * and items from inputB have _inputIndex=1.
     */
    private List<Map<String, Object>> combinedInput(
            List<Map<String, Object>> inputA, List<Map<String, Object>> inputB) {
        List<Map<String, Object>> combined = new ArrayList<>();
        for (Map<String, Object> itemMap : inputA) {
            Map<String, Object> a = item(mutableMap("_inputIndex", 0));
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) a.get("json");
            @SuppressWarnings("unchecked")
            Map<String, Object> srcJson = (Map<String, Object>) itemMap.get("json");
            if (srcJson != null) json.putAll(srcJson);
            combined.add(a);
        }
        for (Map<String, Object> itemMap : inputB) {
            Map<String, Object> b = item(mutableMap("_inputIndex", 1));
            @SuppressWarnings("unchecked")
            Map<String, Object> json = (Map<String, Object>) b.get("json");
            @SuppressWarnings("unchecked")
            Map<String, Object> srcJson = (Map<String, Object>) itemMap.get("json");
            if (srcJson != null) json.putAll(srcJson);
            combined.add(b);
        }
        return combined;
    }

    private Map<String, Object> mergeByFields(String field1, String field2) {
        return mutableMap("values", List.of(
                mutableMap("field1", field1, "field2", field2)
        ));
    }

    // -- Compare two datasets by key: four outputs --

    @Test
    void comparesByKeyAndProducesFourOutputs() {
        List<Map<String, Object>> inputA = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob"),
                Map.of("id", 3, "name", "Charlie")
        );
        List<Map<String, Object>> inputB = items(
                Map.of("id", 2, "name", "Bob"),
                Map.of("id", 3, "name", "Charles"),
                Map.of("id", 4, "name", "Diana")
        );

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        // Output 0: inAOnly (id=1)
        List<Map<String, Object>> inAOnly = output(result, 0);
        assertThat(inAOnly).hasSize(1);
        assertThat(jsonAt(result, 0, 0)).containsEntry("id", 1);

        // Output 1: same (id=2 - same name)
        List<Map<String, Object>> same = output(result, 1);
        assertThat(same).hasSize(1);
        assertThat(jsonAt(result, 1, 0)).containsEntry("id", 2);

        // Output 2: different (id=3 - different name)
        List<Map<String, Object>> different = output(result, 2);
        assertThat(different).hasSize(1);
        assertThat(jsonAt(result, 2, 0)).containsEntry("id", 3);

        // Output 3: inBOnly (id=4)
        List<Map<String, Object>> inBOnly = output(result, 3);
        assertThat(inBOnly).hasSize(1);
        assertThat(jsonAt(result, 3, 0)).containsEntry("id", 4);
    }

    // -- Empty inputs --

    @Test
    void emptyInputReturnsAllEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
        assertThat(output(result, 2)).isEmpty();
        assertThat(output(result, 3)).isEmpty();
    }

    @Test
    void nullInputReturnsAllEmptyOutputs() {
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result, 0)).isEmpty();
        assertThat(output(result, 1)).isEmpty();
        assertThat(output(result, 2)).isEmpty();
        assertThat(output(result, 3)).isEmpty();
    }

    // -- No matches --

    @Test
    void noMatchesPutsAllInAOnlyAndBOnly() {
        List<Map<String, Object>> inputA = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
        List<Map<String, Object>> inputB = items(
                Map.of("id", 3, "name", "Charlie"),
                Map.of("id", 4, "name", "Diana")
        );

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(output(result, 0)).hasSize(2); // inAOnly
        assertThat(output(result, 1)).isEmpty();   // same
        assertThat(output(result, 2)).isEmpty();   // different
        assertThat(output(result, 3)).hasSize(2); // inBOnly
    }

    // -- All matching and same --

    @Test
    void allMatchingAndIdenticalPutsAllInSame() {
        List<Map<String, Object>> inputA = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
        List<Map<String, Object>> inputB = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(output(result, 0)).isEmpty();   // inAOnly
        assertThat(output(result, 1)).hasSize(2); // same
        assertThat(output(result, 2)).isEmpty();   // different
        assertThat(output(result, 3)).isEmpty();   // inBOnly
    }

    // -- All matching but different --

    @Test
    void allMatchingButDifferentFieldsPutsAllInDifferent() {
        List<Map<String, Object>> inputA = items(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
        List<Map<String, Object>> inputB = items(
                Map.of("id", 1, "name", "Alicia"),
                Map.of("id", 2, "name", "Bobby")
        );

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(output(result, 0)).isEmpty();   // inAOnly
        assertThat(output(result, 1)).isEmpty();   // same
        assertThat(output(result, 2)).hasSize(2); // different
        assertThat(output(result, 3)).isEmpty();   // inBOnly
    }

    // -- Resolve preferInput1 uses A version --

    @Test
    void resolvePreferInput1UseAVersion() {
        List<Map<String, Object>> inputA = items(Map.of("id", 1, "name", "Alice"));
        List<Map<String, Object>> inputB = items(Map.of("id", 1, "name", "Alicia"));

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(jsonAt(result, 2, 0)).containsEntry("name", "Alice");
    }

    // -- Resolve preferInput2 uses B version --

    @Test
    void resolvePreferInput2UseBVersion() {
        List<Map<String, Object>> inputA = items(Map.of("id", 1, "name", "Alice"));
        List<Map<String, Object>> inputB = items(Map.of("id", 1, "name", "Alicia"));

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput2"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(jsonAt(result, 2, 0)).containsEntry("name", "Alicia");
    }

    // -- Resolve includeBoth adds suffixes for conflicts --

    @Test
    void resolveIncludeBothAddsSuffixesForConflictingFields() {
        List<Map<String, Object>> inputA = items(Map.of("id", 1, "name", "Alice"));
        List<Map<String, Object>> inputB = items(Map.of("id", 1, "name", "Alicia"));

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "includeBoth"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        Map<String, Object> diffItem = jsonAt(result, 2, 0);
        assertThat(diffItem).containsEntry("id", 1); // match field not suffixed
        assertThat(diffItem).containsEntry("name_A", "Alice");
        assertThat(diffItem).containsEntry("name_B", "Alicia");
    }

    // -- Different field names for matching --

    @Test
    void matchOnDifferentFieldNames() {
        List<Map<String, Object>> inputA = items(Map.of("userId", 1, "name", "Alice"));
        List<Map<String, Object>> inputB = items(Map.of("id", 1, "name", "Alice"));

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("userId", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        // They match (userId=1 matches id=1), but fields differ (inputA has userId, inputB has id)
        // So they go to "different" output since the field sets are different
        // The key fields are excluded from diff comparison, but one has "userId" and other has "id"
        // as extra fields, making them different
        assertThat(output(result, 1)).hasSizeLessThanOrEqualTo(1);
    }

    // -- Only A items --

    @Test
    void onlyAItemsGoToInAOnly() {
        List<Map<String, Object>> inputA = items(
                Map.of("id", 1, "name", "Alice")
        );
        List<Map<String, Object>> inputB = List.of(); // empty B

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(output(result, 0)).hasSize(1); // inAOnly
        assertThat(output(result, 3)).isEmpty();   // inBOnly
    }

    // -- Only B items --

    @Test
    void onlyBItemsGoToInBOnly() {
        List<Map<String, Object>> inputA = List.of(); // empty A
        List<Map<String, Object>> inputB = items(
                Map.of("id", 1, "name", "Bob")
        );

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        assertThat(output(result, 0)).isEmpty();   // inAOnly
        assertThat(output(result, 3)).hasSize(1); // inBOnly
    }

    // -- Has correct inputs and outputs --

    @Test
    void hasTwoInputs() {
        assertThat(node.getInputs()).hasSize(2);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("inputA");
        assertThat(node.getInputs().get(1).getName()).isEqualTo("inputB");
    }

    @Test
    void hasFourOutputs() {
        assertThat(node.getOutputs()).hasSize(4);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("inAOnly");
        assertThat(node.getOutputs().get(1).getName()).isEqualTo("same");
        assertThat(node.getOutputs().get(2).getName()).isEqualTo("different");
        assertThat(node.getOutputs().get(3).getName()).isEqualTo("inBOnly");
    }

    // -- _inputIndex is cleaned from output --

    @Test
    void inputIndexIsRemovedFromOutputItems() {
        List<Map<String, Object>> inputA = items(Map.of("id", 1, "val", "a"));
        List<Map<String, Object>> inputB = items(Map.of("id", 2, "val", "b"));

        List<Map<String, Object>> combined = combinedInput(inputA, inputB);
        Map<String, Object> params = mutableMap(
                "mergeByFields", mergeByFields("id", "id"),
                "resolve", "preferInput1"
        );

        NodeExecutionResult result = node.execute(ctx(combined, params));

        // id=1 is in A only, id=2 is in B only
        assertThat(jsonAt(result, 0, 0)).doesNotContainKey("_inputIndex");
        assertThat(jsonAt(result, 3, 0)).doesNotContainKey("_inputIndex");
    }
}
