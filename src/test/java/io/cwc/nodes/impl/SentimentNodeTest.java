package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class SentimentNodeTest {

    private SentimentNode node;

    @BeforeEach
    void setUp() {
        node = new SentimentNode();
    }

    // ── Positive text gets positive label ──

    @Test
    void positiveTextGetsPositiveLabel() {
        List<Map<String, Object>> input = items(
                Map.of("body", "amazing excellent wonderful")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment).isNotNull();
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isGreaterThan(0.0);
        assertThat(sentiment.get("label")).isEqualTo("positive");
    }

    @Test
    void stronglyPositiveText() {
        List<Map<String, Object>> input = items(
                Map.of("body", "outstanding excellent brilliant fantastic phenomenal")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isGreaterThan(0.5);
        assertThat(sentiment.get("label")).isEqualTo("positive");
    }

    // ── Negative text gets negative label ──

    @Test
    void negativeTextGetsNegativeLabel() {
        List<Map<String, Object>> input = items(
                Map.of("body", "terrible awful horrible")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isLessThan(0.0);
        assertThat(sentiment.get("label")).isEqualTo("negative");
    }

    @Test
    void stronglyNegativeText() {
        List<Map<String, Object>> input = items(
                Map.of("body", "abhorrent abominable detestable heinous reprehensible")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isLessThan(-0.5);
        assertThat(sentiment.get("label")).isEqualTo("negative");
    }

    // ── Neutral text gets neutral label ──

    @Test
    void neutralTextGetsNeutralLabel() {
        // Text with no sentiment words in the lexicon
        List<Map<String, Object>> input = items(
                Map.of("body", "The meeting is scheduled for tomorrow at the office")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isBetween(-0.05, 0.05);
        assertThat(sentiment.get("label")).isEqualTo("neutral");
    }

    // ── Empty text returns neutral with 0 score ──

    @Test
    void emptyTextReturnsNeutralWithZeroScore() {
        List<Map<String, Object>> input = items(
                Map.of("body", "")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(((Number) sentiment.get("score")).doubleValue()).isEqualTo(0.0);
        assertThat(sentiment.get("label")).isEqualTo("neutral");
        assertThat(sentiment.get("wordCount")).isEqualTo(0);
        assertThat(sentiment.get("matchedWords")).isEqualTo(0);
    }

    // ── Custom thresholds affect classification ──

    @Test
    void customThresholdsWidenNeutralBand() {
        // With very wide neutral band, mildly positive text should be "neutral"
        // "good" has score +1, normalized = 1/5 = 0.2
        List<Map<String, Object>> input = items(
                Map.of("body", "This is good")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.9,
                "negativeThreshold", -0.9
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("label")).isEqualTo("neutral");
    }

    @Test
    void customThresholdsNarrowNeutralBand() {
        // With very narrow neutral band, even mildly positive text should be "positive"
        // "good" has score +1, normalized = 1/5 = 0.2
        List<Map<String, Object>> input = items(
                Map.of("body", "This is good")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.01,
                "negativeThreshold", -0.01
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("label")).isEqualTo("positive");
    }

    @Test
    void customThresholdsMildlyNegativeBecomesNeutral() {
        // "bad" has score -2, normalized = -2/5 = -0.4
        List<Map<String, Object>> input = items(
                Map.of("body", "This is bad")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.9,
                "negativeThreshold", -0.9
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("label")).isEqualTo("neutral");
    }

    // ── Custom inputField reads from correct field ──

    @Test
    void customInputFieldReadsFromCorrectField() {
        List<Map<String, Object>> input = items(
                Map.of("content", "amazing wonderful great")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "content",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("label")).isEqualTo("positive");
        // Original field should be preserved
        assertThat(firstJson(result)).containsEntry("content", "amazing wonderful great");
    }

    // ── Custom outputField writes to correct field ──

    @Test
    void customOutputFieldWritesToCorrectField() {
        List<Map<String, Object>> input = items(
                Map.of("body", "amazing wonderful great")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "analysis",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("analysis");
        assertThat(firstJson(result)).doesNotContainKey("sentiment");
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) firstJson(result).get("analysis");
        assertThat(analysis.get("label")).isEqualTo("positive");
    }

    // ── Multiple items processed ──

    @Test
    void multipleItemsProcessed() {
        List<Map<String, Object>> input = items(
                Map.of("body", "This is great and amazing"),
                Map.of("body", "This is terrible and awful"),
                Map.of("body", "The meeting is at noon")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);

        @SuppressWarnings("unchecked")
        Map<String, Object> sent0 = (Map<String, Object>) jsonAt(result, 0).get("sentiment");
        @SuppressWarnings("unchecked")
        Map<String, Object> sent1 = (Map<String, Object>) jsonAt(result, 1).get("sentiment");
        @SuppressWarnings("unchecked")
        Map<String, Object> sent2 = (Map<String, Object>) jsonAt(result, 2).get("sentiment");

        assertThat(sent0.get("label")).isEqualTo("positive");
        assertThat(sent1.get("label")).isEqualTo("negative");
        assertThat(sent2.get("label")).isEqualTo("neutral");
    }

    // ── Score is normalized between -1 and 1 ──

    @Test
    void scoreIsBoundedPositive() {
        // All strongly positive words
        List<Map<String, Object>> input = items(
                Map.of("body", "outstanding superb breathtaking thrilling phenomenal")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isBetween(-1.0, 1.0);
    }

    @Test
    void scoreIsBoundedNegative() {
        // All strongly negative words (repeated to make extreme)
        List<Map<String, Object>> input = items(
                Map.of("body", "abhorrent abominable detestable heinous reprehensible abhorrent abominable detestable heinous reprehensible")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        assertThat(score).isBetween(-1.0, 1.0);
    }

    // ── matchedWords and wordCount are correct ──

    @Test
    void matchedWordsAndWordCountAreCorrect() {
        // "good" (score +1) and "bad" (score -2) are in the lexicon
        // "the", "is", "and" are not in the lexicon
        List<Map<String, Object>> input = items(
                Map.of("body", "the good and bad")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("wordCount")).isEqualTo(4);
        assertThat(sentiment.get("matchedWords")).isEqualTo(2);
    }

    @Test
    void wordCountWithNoMatches() {
        List<Map<String, Object>> input = items(
                Map.of("body", "one two three four five")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("wordCount")).isEqualTo(5);
        assertThat(sentiment.get("matchedWords")).isEqualTo(0);
        assertThat(((Number) sentiment.get("score")).doubleValue()).isEqualTo(0.0);
    }

    // ── Result contains all expected fields ──

    @Test
    void resultContainsAllExpectedFields() {
        List<Map<String, Object>> input = items(
                Map.of("body", "This is a good product that works well")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment).containsKey("score");
        assertThat(sentiment).containsKey("label");
        assertThat(sentiment).containsKey("wordCount");
        assertThat(sentiment).containsKey("matchedWords");
        assertThat(sentiment).containsKey("magnitude");
    }

    // ── Magnitude is absolute value of score ──

    @Test
    void magnitudeIsAbsoluteOfScore() {
        List<Map<String, Object>> input = items(
                Map.of("body", "horrible terrible awful")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        double score = ((Number) sentiment.get("score")).doubleValue();
        double magnitude = ((Number) sentiment.get("magnitude")).doubleValue();
        assertThat(magnitude).isEqualTo(Math.abs(score));
    }

    @Test
    void magnitudeIsZeroForEmptyText() {
        List<Map<String, Object>> input = items(
                Map.of("body", "")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(((Number) sentiment.get("magnitude")).doubleValue()).isEqualTo(0.0);
    }

    // ── Empty/null input returns empty ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment"
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── Original fields preserved ──

    @Test
    void originalFieldsPreserved() {
        List<Map<String, Object>> input = items(
                Map.of("body", "great product", "author", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("body", "great product");
        assertThat(firstJson(result)).containsEntry("author", "Alice");
        assertThat(firstJson(result)).containsKey("sentiment");
    }

    // ── Mixed sentiment text ──

    @Test
    void mixedSentimentText() {
        List<Map<String, Object>> input = items(
                Map.of("body", "The product is great but the service was terrible")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        // Score should reflect mixed sentiment -- matched words present
        assertThat(((Number) sentiment.get("matchedWords")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(sentiment).containsKey("score");
    }

    // ── Case insensitivity ──

    @Test
    void textIsCaseInsensitive() {
        // "AMAZING" should match "amazing" in the lexicon
        List<Map<String, Object>> input = items(
                Map.of("body", "AMAZING EXCELLENT WONDERFUL")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "body",
                "outputField", "sentiment",
                "positiveThreshold", 0.05,
                "negativeThreshold", -0.05
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> sentiment = (Map<String, Object>) firstJson(result).get("sentiment");
        assertThat(sentiment.get("label")).isEqualTo("positive");
        assertThat(((Number) sentiment.get("matchedWords")).intValue()).isEqualTo(3);
    }
}
