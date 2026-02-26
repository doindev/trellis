package io.trellis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendExecutionStarted(String executionId, String workflowId) {
        send("/topic/execution/" + executionId, Map.of(
                "event", "executionStarted",
                "executionId", executionId,
                "workflowId", workflowId
        ));
    }

    public void sendNodeStarted(String executionId, String nodeId, String nodeName) {
        send("/topic/execution/" + executionId, Map.of(
                "event", "nodeStarted",
                "executionId", executionId,
                "nodeId", nodeId,
                "nodeName", nodeName
        ));
    }

    public void sendNodeFinished(String executionId, String nodeId, String nodeName,
                                 String status, Object outputData,
                                 long executionTimeMs, int executionOrder) {
        sendNodeFinished(executionId, nodeId, nodeName, status, outputData,
                executionTimeMs, executionOrder, null);
    }

    public void sendNodeFinished(String executionId, String nodeId, String nodeName,
                                 String status, Object outputData,
                                 long executionTimeMs, int executionOrder,
                                 String errorMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", "nodeFinished");
        payload.put("executionId", executionId);
        payload.put("nodeId", nodeId);
        payload.put("nodeName", nodeName);
        payload.put("status", status);
        payload.put("data", outputData != null ? outputData : Map.of());
        payload.put("executionTime", executionTimeMs);
        payload.put("executionOrder", executionOrder);
        if (errorMessage != null) {
            payload.put("error", errorMessage);
        }
        send("/topic/execution/" + executionId, payload);
    }

    public void sendExecutionFinished(String executionId, String status, Object resultData) {
        // Only send minimal payload via WebSocket to avoid exceeding message size limits.
        // Full result data is persisted by ExecutionService.finish() and available via REST API.
        send("/topic/execution/" + executionId, Map.of(
                "event", "executionFinished",
                "executionId", executionId,
                "status", status
        ));
    }

    public void sendExecutionWaiting(String executionId, String nodeId, String waitType) {
        send("/topic/execution/" + executionId, Map.of(
                "event", "executionWaiting",
                "executionId", executionId,
                "nodeId", nodeId,
                "waitType", waitType
        ));
    }

    public void sendWebhookTestData(String workflowId, Object data) {
        send("/topic/webhook-test/" + workflowId, Map.of(
                "event", "testData",
                "workflowId", workflowId,
                "data", data != null ? data : Map.of()
        ));
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Failed to send WebSocket message to {}: {}", destination, e.getMessage());
        }
    }
}
