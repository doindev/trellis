package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SummarizeNodeTest {

    private SummarizeNode node;

    @BeforeEach
    void setUp() {
        node = new SummarizeNode();
    }

    // ── Count aggregation ──

    @Test
    void countAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "amount", 10),
                Map.of("name", "Bob", "amount", 20),
                Map.of("name", "Charlie", "amount", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "name", "aggregation", "count")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        // count uses total item count, not value count
        assertThat(firstJson(result).get("count_name")).isEqualTo(3);
    }

    @Test
    void countSingleItem() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "name", "aggregation", "count")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("count_name")).isEqualTo(1);
    }

    // ── CountUnique aggregation ──

    @Test
    void countUniqueAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("color", "red"),
                Map.of("color", "blue"),
                Map.of("color", "red"),
                Map.of("color", "green"),
                Map.of("color", "blue")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "color", "aggregation", "countUnique")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("countUnique_color")).isEqualTo(3);
    }

    @Test
    void countUniqueAllSameValues() {
        List<Map<String, Object>> input = items(
                Map.of("status", "active"),
                Map.of("status", "active"),
                Map.of("status", "active")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "status", "aggregation", "countUnique")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("countUnique_status")).isEqualTo(1);
    }

    // ── Sum aggregation ──

    @Test
    void sumAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("amount", 10),
                Map.of("amount", 20),
                Map.of("amount", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(((Number) firstJson(result).get("sum_amount")).doubleValue()).isEqualTo(60.0);
    }

    @Test
    void sumWithStringNumbers() {
        List<Map<String, Object>> input = items(
                Map.of("amount", "10.5"),
                Map.of("amount", "20.5")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("sum_amount")).doubleValue()).isEqualTo(31.0);
    }

    @Test
    void sumWithDecimalValues() {
        List<Map<String, Object>> input = items(
                Map.of("price", 9.99),
                Map.of("price", 14.50),
                Map.of("price", 5.51)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "price", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("sum_price")).doubleValue()).isEqualTo(30.0);
    }

    // ── Average aggregation ──

    @Test
    void averageAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("score", 10),
                Map.of("score", 20),
                Map.of("score", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "score", "aggregation", "average")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(((Number) firstJson(result).get("average_score")).doubleValue()).isEqualTo(20.0);
    }

    @Test
    void averageSingleItem() {
        List<Map<String, Object>> input = items(
                Map.of("value", 42)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "value", "aggregation", "average")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("average_value")).doubleValue()).isEqualTo(42.0);
    }

    // ── Min aggregation ──

    @Test
    void minAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("value", 50),
                Map.of("value", 10),
                Map.of("value", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "value", "aggregation", "min")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("min_value")).doubleValue()).isEqualTo(10.0);
    }

    @Test
    void minWithNegativeValues() {
        List<Map<String, Object>> input = items(
                Map.of("temp", -5),
                Map.of("temp", 10),
                Map.of("temp", -20)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "temp", "aggregation", "min")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("min_temp")).doubleValue()).isEqualTo(-20.0);
    }

    // ── Max aggregation ──

    @Test
    void maxAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("value", 50),
                Map.of("value", 10),
                Map.of("value", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "value", "aggregation", "max")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("max_value")).doubleValue()).isEqualTo(50.0);
    }

    @Test
    void maxWithNegativeValues() {
        List<Map<String, Object>> input = items(
                Map.of("temp", -5),
                Map.of("temp", -10),
                Map.of("temp", -1)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "temp", "aggregation", "max")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(((Number) firstJson(result).get("max_temp")).doubleValue()).isEqualTo(-1.0);
    }

    // ── Concatenate aggregation ──

    @Test
    void concatenateAggregation() {
        List<Map<String, Object>> input = items(
                Map.of("tag", "java"),
                Map.of("tag", "spring"),
                Map.of("tag", "maven")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "tag", "aggregation", "concatenate")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        String concatenated = (String) firstJson(result).get("concatenate_tag");
        assertThat(concatenated).isEqualTo("java, spring, maven");
    }

    @Test
    void concatenateWithCustomSeparator() {
        List<Map<String, Object>> input = items(
                Map.of("tag", "a"),
                Map.of("tag", "b"),
                Map.of("tag", "c")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "tag", "aggregation", "concatenate")
                ),
                "options", mutableMap("separator", " | ")
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("concatenate_tag")).isEqualTo("a | b | c");
    }

    @Test
    void concatenateSingleItem() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "name", "aggregation", "concatenate")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("concatenate_name")).isEqualTo("Alice");
    }

    // ── splitBy grouping ──

    @Test
    void groupBySingleField() {
        List<Map<String, Object>> input = items(
                Map.of("category", "fruit", "amount", 10),
                Map.of("category", "fruit", "amount", 20),
                Map.of("category", "veggie", "amount", 30),
                Map.of("category", "veggie", "amount", 40)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                ),
                "splitBy", "category"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);

        // Find fruit and veggie groups
        Map<String, Object> fruitGroup = null;
        Map<String, Object> veggieGroup = null;
        for (int i = 0; i < output(result).size(); i++) {
            Map<String, Object> json = jsonAt(result, i);
            if ("fruit".equals(json.get("category"))) fruitGroup = json;
            if ("veggie".equals(json.get("category"))) veggieGroup = json;
        }

        assertThat(fruitGroup).isNotNull();
        assertThat(((Number) fruitGroup.get("sum_amount")).doubleValue()).isEqualTo(30.0);
        assertThat(veggieGroup).isNotNull();
        assertThat(((Number) veggieGroup.get("sum_amount")).doubleValue()).isEqualTo(70.0);
    }

    @Test
    void groupByMultipleFields() {
        List<Map<String, Object>> input = items(
                Map.of("category", "fruit", "color", "red", "amount", 10),
                Map.of("category", "fruit", "color", "red", "amount", 20),
                Map.of("category", "fruit", "color", "green", "amount", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                ),
                "splitBy", "category, color"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);

        // Find the two groups
        Map<String, Object> redGroup = null;
        Map<String, Object> greenGroup = null;
        for (int i = 0; i < output(result).size(); i++) {
            Map<String, Object> json = jsonAt(result, i);
            if ("red".equals(json.get("color"))) redGroup = json;
            if ("green".equals(json.get("color"))) greenGroup = json;
        }

        assertThat(redGroup).isNotNull();
        assertThat(((Number) redGroup.get("sum_amount")).doubleValue()).isEqualTo(30.0);
        assertThat(greenGroup).isNotNull();
        assertThat(((Number) greenGroup.get("sum_amount")).doubleValue()).isEqualTo(30.0);
    }

    @Test
    void groupByWithCount() {
        List<Map<String, Object>> input = items(
                Map.of("status", "active", "name", "Alice"),
                Map.of("status", "active", "name", "Bob"),
                Map.of("status", "inactive", "name", "Charlie")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "name", "aggregation", "count")
                ),
                "splitBy", "status"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);

        Map<String, Object> activeGroup = null;
        Map<String, Object> inactiveGroup = null;
        for (int i = 0; i < output(result).size(); i++) {
            Map<String, Object> json = jsonAt(result, i);
            if ("active".equals(json.get("status"))) activeGroup = json;
            if ("inactive".equals(json.get("status"))) inactiveGroup = json;
        }

        assertThat(activeGroup).isNotNull();
        assertThat(activeGroup.get("count_name")).isEqualTo(2);
        assertThat(inactiveGroup).isNotNull();
        assertThat(inactiveGroup.get("count_name")).isEqualTo(1);
    }

    // ── Multiple aggregation fields at once ──

    @Test
    void multipleAggregations() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "score", 80),
                Map.of("name", "Bob", "score", 90),
                Map.of("name", "Charlie", "score", 70)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "name", "aggregation", "count"),
                        mutableMap("field", "score", "aggregation", "average"),
                        mutableMap("field", "score", "aggregation", "max")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result).get("count_name")).isEqualTo(3);
        assertThat(((Number) firstJson(result).get("average_score")).doubleValue()).isEqualTo(80.0);
        assertThat(((Number) firstJson(result).get("max_score")).doubleValue()).isEqualTo(90.0);
    }

    @Test
    void multipleDifferentAggregationsOnSameField() {
        List<Map<String, Object>> input = items(
                Map.of("amount", 10),
                Map.of("amount", 20),
                Map.of("amount", 30)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum"),
                        mutableMap("field", "amount", "aggregation", "min"),
                        mutableMap("field", "amount", "aggregation", "max"),
                        mutableMap("field", "amount", "aggregation", "average")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(((Number) firstJson(result).get("sum_amount")).doubleValue()).isEqualTo(60.0);
        assertThat(((Number) firstJson(result).get("min_amount")).doubleValue()).isEqualTo(10.0);
        assertThat(((Number) firstJson(result).get("max_amount")).doubleValue()).isEqualTo(30.0);
        assertThat(((Number) firstJson(result).get("average_amount")).doubleValue()).isEqualTo(20.0);
    }

    // ── No fieldsToSummarize returns error ──

    @Test
    void noFieldsToSummarizeReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap();

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("No fields to summarize");
    }

    @Test
    void emptyFieldsToSummarizeListReturnsError() {
        List<Map<String, Object>> input = items(Map.of("x", 1));
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of()
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("No fields to summarize");
    }

    // ── Empty/null input returns empty ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── continueIfFieldNotFound=false with missing field returns error ──

    @Test
    void continueIfFieldNotFoundFalseWithMissingFieldReturnsError() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "nonexistent", "aggregation", "sum")
                ),
                "options", mutableMap("continueIfFieldNotFound", false)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNotNull();
        assertThat(result.getError().getMessage()).contains("nonexistent");
        assertThat(result.getError().getMessage()).contains("not found");
    }

    @Test
    void continueIfFieldNotFoundTrueWithMissingFieldSucceeds() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "nonexistent", "aggregation", "sum")
                ),
                "options", mutableMap("continueIfFieldNotFound", true)
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(result.getError()).isNull();
        assertThat(output(result)).hasSize(1);
    }

    // ── fieldsToSummarize as Map with "values" key ──

    @Test
    void fieldsToSummarizeAsMapWithValuesKey() {
        List<Map<String, Object>> input = items(
                Map.of("amount", 10),
                Map.of("amount", 20)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", mutableMap("values", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                ))
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(((Number) firstJson(result).get("sum_amount")).doubleValue()).isEqualTo(30.0);
    }

    // ── Output key format is aggregation_field ──

    @Test
    void outputKeyFormatIsAggregationUnderscoreField() {
        List<Map<String, Object>> input = items(
                Map.of("amount", 10),
                Map.of("amount", 20)
        );
        Map<String, Object> params = mutableMap(
                "fieldsToSummarize", List.of(
                        mutableMap("field", "amount", "aggregation", "sum")
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("sum_amount");
    }
}
