package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionContext;
import io.cwc.nodes.core.NodeExecutionResult;

class RespondToWebhookNodeTest {

    private RespondToWebhookNode node;

    @BeforeEach
    void setUp() {
        node = new RespondToWebhookNode();
    }

    // -- Helper to build context with static data --

    private NodeExecutionContext ctxWithStaticData(
            List<Map<String, Object>> input, Map<String, Object> params) {
        return contextBuilder()
                .inputData(input)
                .parameters(params)
                .staticData(new HashMap<>())
                .build();
    }

    // -- allIncomingItems mode --

    @Test
    void allIncomingItemsModeReturnsAllItemsAsResponseBody() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "respondWith", "allIncomingItems",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse).isNotNull();
        assertThat(webhookResponse.get("body")).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Object> body = (List<Object>) webhookResponse.get("body");
        assertThat(body).hasSize(2);
    }

    @Test
    void allIncomingItemsModeWithResponseKeyWrapsInKey() {
        List<Map<String, Object>> input = items(Map.of("name", "Alice"));
        Map<String, Object> params = mutableMap(
                "respondWith", "allIncomingItems",
                "options", mutableMap("responseKey", "results")
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsKey("results");
    }

    // -- firstIncomingItem mode --

    @Test
    void firstIncomingItemModeReturnsFirstItemJsonAsBody() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice", "role", "admin"),
                Map.of("name", "Bob", "role", "user")
        );
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsEntry("name", "Alice");
        assertThat(body).containsEntry("role", "admin");
    }

    @Test
    void firstIncomingItemModeWithNoInputReturnsEmptyMap() {
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(List.of(), params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo(Map.of());
    }

    @Test
    void firstIncomingItemModeWithResponseKeyWrapsInKey() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap("responseKey", "data")
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsKey("data");
    }

    // -- JSON response mode --

    @Test
    void jsonModeReturnsCustomJsonAsResponseBody() {
        List<Map<String, Object>> input = items(Map.of("pass", "through"));
        Map<String, Object> params = mutableMap(
                "respondWith", "json",
                "responseJson", "{\"status\": \"ok\", \"code\": 200}",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsEntry("status", "ok");
        assertThat(body).containsEntry("code", 200);
    }

    @Test
    void jsonModeWithInvalidJsonUsesRawString() {
        List<Map<String, Object>> input = items(Map.of("x", "y"));
        Map<String, Object> params = mutableMap(
                "respondWith", "json",
                "responseJson", "not-json",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo("not-json");
    }

    // -- Text response mode --

    @Test
    void textModeReturnsTextAsResponseBody() {
        List<Map<String, Object>> input = items(Map.of("x", "y"));
        Map<String, Object> params = mutableMap(
                "respondWith", "text",
                "responseBody", "Workflow completed successfully",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo("Workflow completed successfully");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) webhookResponse.get("headers");
        assertThat(headers).containsEntry("Content-Type", "text/plain");
    }

    // -- noData mode --

    @Test
    void noDataModeReturnsNullBody() {
        List<Map<String, Object>> input = items(Map.of("x", "y"));
        Map<String, Object> params = mutableMap(
                "respondWith", "noData",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isNull();
    }

    // -- Redirect mode --

    @Test
    void redirectModeSetsLocationHeaderAndStatusCode302() {
        List<Map<String, Object>> input = items(Map.of("x", "y"));
        Map<String, Object> params = mutableMap(
                "respondWith", "redirect",
                "redirectURL", "https://www.example.com",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("statusCode")).isEqualTo(302);
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) webhookResponse.get("headers");
        assertThat(headers).containsEntry("Location", "https://www.example.com");
    }

    @Test
    void redirectModeBodyIsNull() {
        List<Map<String, Object>> input = items(Map.of("x", "y"));
        Map<String, Object> params = mutableMap(
                "respondWith", "redirect",
                "redirectURL", "https://www.example.com",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isNull();
    }

    // -- Custom response code --

    @Test
    void customResponseCodeIsApplied() {
        List<Map<String, Object>> input = items(Map.of("created", true));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap("responseCode", 201)
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("statusCode")).isEqualTo(201);
    }

    @Test
    void defaultResponseCodeIs200() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse.get("statusCode")).isEqualTo(200);
    }

    // -- Response stored in static data --

    @Test
    void responseIsStoredInStaticDataUnderWebhookResponseKey() {
        List<Map<String, Object>> input = items(Map.of("result", "success"));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        assertThat(result.getStaticData()).containsKey("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        assertThat(webhookResponse).containsKey("statusCode");
        assertThat(webhookResponse).containsKey("headers");
        assertThat(webhookResponse).containsKey("body");
    }

    // -- Input data is passed through in output --

    @Test
    void inputDataIsPassedThroughToOutput() {
        List<Map<String, Object>> input = items(
                Map.of("name", "Alice"),
                Map.of("name", "Bob")
        );
        Map<String, Object> params = mutableMap(
                "respondWith", "allIncomingItems",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        assertThat(output(result)).hasSize(2);
    }

    @Test
    void noInputDataProducesWebhookResponseSetIndicator() {
        Map<String, Object> params = mutableMap(
                "respondWith", "noData",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("webhookResponseSet", true);
    }

    // -- Content-Type header --

    @Test
    void jsonContentTypeIsDefaultForItemModes() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap()
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) webhookResponse.get("headers");
        assertThat(headers).containsEntry("Content-Type", "application/json");
    }

    // -- Custom response headers --

    @Test
    void customResponseHeadersAreIncluded() {
        List<Map<String, Object>> input = items(Map.of("id", 1));
        Map<String, Object> params = mutableMap(
                "respondWith", "firstIncomingItem",
                "options", mutableMap(
                        "responseHeaders", List.of(
                                mutableMap("name", "X-Custom", "value", "myvalue")
                        )
                )
        );

        NodeExecutionResult result = node.execute(ctxWithStaticData(input, params));

        @SuppressWarnings("unchecked")
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getStaticData().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) webhookResponse.get("headers");
        assertThat(headers).containsEntry("X-Custom", "myvalue");
    }

    // -- Has inputs and outputs --

    @Test
    void hasOneInput() {
        assertThat(node.getInputs()).hasSize(1);
        assertThat(node.getInputs().get(0).getName()).isEqualTo("main");
    }

    @Test
    void hasOneOutput() {
        assertThat(node.getOutputs()).hasSize(1);
        assertThat(node.getOutputs().get(0).getName()).isEqualTo("main");
    }
}
