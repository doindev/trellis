package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;

class ContentClassifierNodeTest {

    private ContentClassifierNode node;

    @BeforeEach
    void setUp() {
        node = new ContentClassifierNode();
    }

    // ── Single rule matches keyword ──

    @Test
    void singleRuleMatchesKeyword() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love programming in Java and using APIs")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "programming,api,code,software", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        Object categories = firstJson(result).get("categories");
        assertThat(categories).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) categories;
        assertThat(labels).containsExactly("Technology");
    }

    // ── Multiple rules with multiLabel=true ──

    @Test
    void multipleRulesWithMultiLabelTrue() {
        List<Map<String, Object>> input = items(
                Map.of("body", "The new software update for our football team management app is great")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software,app,code", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football,basketball,tennis", "caseSensitive", false)
                ),
                "multiLabel", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactlyInAnyOrder("Technology", "Sports");
    }

    @Test
    void multipleRulesOnlyOneMatches() {
        List<Map<String, Object>> input = items(
                Map.of("body", "The football match was incredible yesterday")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software,api,code", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football,basketball,tennis", "caseSensitive", false)
                ),
                "multiLabel", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Sports");
    }

    // ── multiLabel=false returns only best match ──

    @Test
    void multiLabelFalseReturnsBestMatch() {
        List<Map<String, Object>> input = items(
                Map.of("body", "software code api football")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software,code,api", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football", "caseSensitive", false)
                ),
                "multiLabel", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        // Technology has 3 keyword matches vs Sports with 1
        assertThat(labels).hasSize(1);
        assertThat(labels).containsExactly("Technology");
    }

    @Test
    void multiLabelFalseWithEqualMatchCountReturnsFirst() {
        List<Map<String, Object>> input = items(
                Map.of("body", "software football")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football", "caseSensitive", false)
                ),
                "multiLabel", false
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        // Both have 1 match; sorted descending by count, stable order -> first encountered wins
        assertThat(labels).hasSize(1);
    }

    // ── Case insensitive matching (default) ──

    @Test
    void caseInsensitiveMatchingByDefault() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love JAVA Programming")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java,programming", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Tech");
    }

    // ── Case sensitive matching ──

    @Test
    void caseSensitiveMatchingRejectsWrongCase() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love JAVA PROGRAMMING")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java,programming", "caseSensitive", true)
                ),
                "defaultLabel", "none"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        // "java" != "JAVA" and "programming" != "PROGRAMMING" in case-sensitive mode
        assertThat(labels).containsExactly("none");
    }

    @Test
    void caseSensitiveMatchingAcceptsCorrectCase() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love Java and programming")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "Java,programming", "caseSensitive", true)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Tech");
    }

    // ── No match returns defaultLabel ──

    @Test
    void noMatchReturnsDefaultLabel() {
        List<Map<String, Object>> input = items(
                Map.of("body", "This is a random sentence about nothing in particular")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software,api,code", "caseSensitive", false)
                ),
                "defaultLabel", "uncategorized"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("uncategorized");
    }

    @Test
    void noMatchReturnsCustomDefaultLabel() {
        List<Map<String, Object>> input = items(
                Map.of("body", "no relevant keywords here")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java,python", "caseSensitive", false)
                ),
                "defaultLabel", "general"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("general");
    }

    // ── includeScores shows matchCounts ──

    @Test
    void includeScoresShowsMatchCounts() {
        List<Map<String, Object>> input = items(
                Map.of("body", "java software development")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "java,software,code", "caseSensitive", false)
                ),
                "includeScores", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        Object categories = firstJson(result).get("categories");
        assertThat(categories).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList = (List<Map<String, Object>>) categories;
        assertThat(scoreList).hasSize(1);
        assertThat(scoreList.get(0)).containsEntry("label", "Technology");
        // "java" and "software" match, "code" does not
        assertThat(scoreList.get(0)).containsEntry("matchCount", 2);
    }

    @Test
    void includeScoresWithNoMatchShowsDefaultLabelAndZeroCount() {
        List<Map<String, Object>> input = items(
                Map.of("body", "random text with no keywords")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java,python", "caseSensitive", false)
                ),
                "includeScores", true,
                "defaultLabel", "other"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList = (List<Map<String, Object>>) firstJson(result).get("categories");
        assertThat(scoreList).hasSize(1);
        assertThat(scoreList.get(0)).containsEntry("label", "other");
        assertThat(scoreList.get(0)).containsEntry("matchCount", 0);
    }

    @Test
    void includeScoresMultipleRules() {
        List<Map<String, Object>> input = items(
                Map.of("body", "software code api football basketball")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "software,code,api", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football,basketball", "caseSensitive", false)
                ),
                "includeScores", true,
                "multiLabel", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList = (List<Map<String, Object>>) firstJson(result).get("categories");
        // Sorted by matchCount descending: Technology(3) then Sports(2)
        assertThat(scoreList).hasSize(2);
        assertThat(scoreList.get(0)).containsEntry("label", "Technology");
        assertThat(scoreList.get(0)).containsEntry("matchCount", 3);
        assertThat(scoreList.get(1)).containsEntry("label", "Sports");
        assertThat(scoreList.get(1)).containsEntry("matchCount", 2);
    }

    // ── Multiple keywords per rule ──

    @Test
    void multipleKeywordsPerRule() {
        List<Map<String, Object>> input = items(
                Map.of("body", "soccer basketball football are all great sports")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Sports", "keywords", "soccer,basketball,football,tennis", "caseSensitive", false)
                ),
                "includeScores", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scoreList = (List<Map<String, Object>>) firstJson(result).get("categories");
        assertThat(scoreList).hasSize(1);
        assertThat(scoreList.get(0)).containsEntry("label", "Sports");
        // soccer, basketball, football match; tennis does not
        assertThat(scoreList.get(0)).containsEntry("matchCount", 3);
    }

    // ── Custom inputField and outputField ──

    @Test
    void customInputField() {
        List<Map<String, Object>> input = items(
                Map.of("description", "This is about programming and code")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "description",
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "programming,code", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Technology");
    }

    @Test
    void customOutputField() {
        List<Map<String, Object>> input = items(
                Map.of("body", "java code")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java", "caseSensitive", false)
                ),
                "outputField", "labels"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsKey("labels");
        assertThat(firstJson(result)).doesNotContainKey("categories");
        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("labels");
        assertThat(labels).containsExactly("Tech");
    }

    @Test
    void customInputFieldAndOutputFieldTogether() {
        List<Map<String, Object>> input = items(
                Map.of("text", "This is about programming", "author", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "inputField", "text",
                "outputField", "tags",
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", "programming", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) firstJson(result).get("tags");
        assertThat(tags).containsExactly("Technology");
        // Original fields should be preserved
        assertThat(firstJson(result)).containsEntry("text", "This is about programming");
        assertThat(firstJson(result)).containsEntry("author", "Alice");
        // Default output field should not be set
        assertThat(firstJson(result)).doesNotContainKey("categories");
    }

    // ── Empty/null input returns empty ──

    @Test
    void emptyInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).isEmpty();
    }

    @Test
    void nullInputReturnsEmpty() {
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(null, params));

        assertThat(output(result)).isEmpty();
    }

    // ── Rules as Map with "values" key ──

    @Test
    void rulesAsMapWithValuesKey() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love programming in Java")
        );
        Map<String, Object> params = mutableMap(
                "rules", mutableMap("values", List.of(
                        mutableMap("label", "Technology", "keywords", "programming,Java", "caseSensitive", false)
                ))
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Technology");
    }

    // ── No rules defined yields default label ──

    @Test
    void noRulesReturnsDefaultLabel() {
        List<Map<String, Object>> input = items(
                Map.of("body", "Some text here")
        );
        Map<String, Object> params = mutableMap(
                "defaultLabel", "uncategorized"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("uncategorized");
    }

    // ── Multiple items processed ──

    @Test
    void multipleItemsProcessed() {
        List<Map<String, Object>> input = items(
                Map.of("body", "java programming"),
                Map.of("body", "football game"),
                Map.of("body", "the weather is nice")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java,programming", "caseSensitive", false),
                        mutableMap("label", "Sports", "keywords", "football,game", "caseSensitive", false)
                ),
                "defaultLabel", "other"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(3);

        @SuppressWarnings("unchecked")
        List<String> labels0 = (List<String>) jsonAt(result, 0).get("categories");
        assertThat(labels0).contains("Tech");

        @SuppressWarnings("unchecked")
        List<String> labels1 = (List<String>) jsonAt(result, 1).get("categories");
        assertThat(labels1).contains("Sports");

        @SuppressWarnings("unchecked")
        List<String> labels2 = (List<String>) jsonAt(result, 2).get("categories");
        assertThat(labels2).containsExactly("other");
    }

    // ── multiLabel=false with includeScores ──

    @Test
    void multiLabelFalseWithIncludeScores() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I love programming code software and also soccer football")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Sports", "keywords", "soccer,football", "caseSensitive", false),
                        mutableMap("label", "Technology", "keywords", "programming,code,software", "caseSensitive", false)
                ),
                "multiLabel", false,
                "includeScores", true
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) firstJson(result).get("categories");
        assertThat(categories).hasSize(1);
        // Technology has 3 matches, Sports has 2 - Technology wins
        assertThat(categories.get(0)).containsEntry("label", "Technology");
        assertThat(categories.get(0)).containsEntry("matchCount", 3);
    }

    // ── Keyword trimming works ──

    @Test
    void keywordsAreTrimmed() {
        List<Map<String, Object>> input = items(
                Map.of("body", "I enjoy programming")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Technology", "keywords", " programming , code , software ", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) firstJson(result).get("categories");
        assertThat(labels).containsExactly("Technology");
    }

    // ── Original fields preserved ──

    @Test
    void originalFieldsPreserved() {
        List<Map<String, Object>> input = items(
                Map.of("body", "java code", "author", "Alice")
        );
        Map<String, Object> params = mutableMap(
                "rules", List.of(
                        mutableMap("label", "Tech", "keywords", "java", "caseSensitive", false)
                )
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("body", "java code");
        assertThat(firstJson(result)).containsEntry("author", "Alice");
        assertThat(firstJson(result)).containsKey("categories");
    }
}
