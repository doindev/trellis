package io.cwc.nodes.impl;

import static io.cwc.nodes.impl.NodeTestHelper.*;
import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.cwc.nodes.core.NodeExecutionResult;
import io.cwc.service.SecurityChainInfoService;

import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

class WebhookNodeTest {

    private WebhookNode node;

    @BeforeEach
    void setUp() {
        node = new WebhookNode();
        // Inject a mock SecurityChainInfoService since it is @Autowired
        SecurityChainInfoService mockService = mock(SecurityChainInfoService.class);
        when(mockService.getAvailableChains()).thenReturn(List.of());
        ReflectionTestUtils.setField(node, "securityChainInfoService", mockService);
    }

    // -- Creates trigger item with webhook metadata (no input data) --

    @Test
    void noInputDataProducesTriggerItemWithWebhookMetadata() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "GET",
                "path", "/my-webhook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("method", "GET");
        assertThat(firstJson(result)).containsEntry("path", "/my-webhook");
        assertThat(firstJson(result)).containsEntry("_webhookPath", "/my-webhook");
        assertThat(firstJson(result)).containsEntry("_webhookMethod", "GET");
        assertThat(firstJson(result)).containsKey("_webhookTimestamp");
        assertThat(firstJson(result)).containsEntry("_webhookAuthentication", "none");
        assertThat(firstJson(result)).containsEntry("responseMode", "onReceived");
    }

    @Test
    void noInputDataContainsEmptyHeadersParamsQueryBody() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("headers");
        assertThat(firstJson(result)).containsKey("params");
        assertThat(firstJson(result)).containsKey("query");
        assertThat(firstJson(result)).containsKey("body");
    }

    // -- Different HTTP methods --

    @Test
    void getMethodIsSetCorrectly() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "GET",
                "path", "test",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("method", "GET");
        assertThat(firstJson(result)).containsEntry("_webhookMethod", "GET");
    }

    @Test
    void postMethodIsSetCorrectly() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "test",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("method", "POST");
        assertThat(firstJson(result)).containsEntry("_webhookMethod", "POST");
    }

    // -- Path parameter --

    @Test
    void customPathIsReflectedInOutput() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "GET",
                "path", "webhook/:id/process",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("path", "webhook/:id/process");
        assertThat(firstJson(result)).containsEntry("_webhookPath", "webhook/:id/process");
    }

    @Test
    void defaultPathIsSlash() {
        // No path parameter specified, defaults to "/"
        Map<String, Object> params = mutableMap(
                "httpMethod", "GET",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsEntry("path", "/");
    }

    // -- With incoming data (simulating HTTP handler data) --

    @Test
    void incomingDataIsEnrichedWithWebhookMetadata() {
        List<Map<String, Object>> input = items(
                mutableMap("body", Map.of("name", "test"), "headers", Map.of("content-type", "application/json"))
        );
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "/api/hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(1);
        assertThat(firstJson(result)).containsEntry("_webhookPath", "/api/hook");
        assertThat(firstJson(result)).containsEntry("_webhookMethod", "POST");
        assertThat(firstJson(result)).containsKey("_webhookTimestamp");
        assertThat(firstJson(result)).containsEntry("_webhookAuthentication", "none");
    }

    @Test
    void incomingDataPreservesExistingBodyAndHeaders() {
        Map<String, Object> bodyData = Map.of("field", "value");
        Map<String, Object> headerData = Map.of("Authorization", "Bearer xyz");
        List<Map<String, Object>> input = items(
                mutableMap("body", bodyData, "headers", headerData)
        );
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result).get("body")).isEqualTo(bodyData);
        assertThat(firstJson(result).get("headers")).isEqualTo(headerData);
    }

    @Test
    void incomingDataWithoutMethodGetsDefaultMethodFromParam() {
        // Input does not have "method" field, so putIfAbsent fills it from httpMethod param
        List<Map<String, Object>> input = items(
                mutableMap("body", Map.of("data", "payload"))
        );
        Map<String, Object> params = mutableMap(
                "httpMethod", "PUT",
                "path", "hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("method", "PUT");
    }

    @Test
    void incomingDataWithExistingMethodIsNotOverwritten() {
        // Input already has "method" field, putIfAbsent should not overwrite
        List<Map<String, Object>> input = items(
                mutableMap("method", "PATCH", "body", Map.of())
        );
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(firstJson(result)).containsEntry("method", "PATCH");
    }

    @Test
    void multipleIncomingItemsAreAllEnriched() {
        List<Map<String, Object>> input = items(
                mutableMap("body", Map.of("id", 1)),
                mutableMap("body", Map.of("id", 2))
        );
        Map<String, Object> params = mutableMap(
                "httpMethod", "POST",
                "path", "multi-hook",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(input, params));

        assertThat(output(result)).hasSize(2);
        assertThat(jsonAt(result, 0)).containsEntry("_webhookPath", "multi-hook");
        assertThat(jsonAt(result, 1)).containsEntry("_webhookPath", "multi-hook");
    }

    // -- Trigger timestamp from base class --

    @Test
    void noInputDataContainsTriggerTimestamp() {
        Map<String, Object> params = mutableMap(
                "httpMethod", "GET",
                "path", "test",
                "authentication", "none",
                "responseMode", "onReceived"
        );

        NodeExecutionResult result = node.execute(ctx(List.of(), params));

        assertThat(firstJson(result)).containsKey("_triggerTimestamp");
    }

    // -- No inputs (trigger node) --

    @Test
    void hasNoInputs() {
        assertThat(node.getInputs()).isEmpty();
    }

    // -- Has one output --

    @Test
    void hasOneMainOutput() {
        assertThat(node.getOutputs()).hasSize(1);
    }
}
