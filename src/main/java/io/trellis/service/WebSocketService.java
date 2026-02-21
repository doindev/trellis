package io.trellis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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

    public void sendNodeFinished(String executionId, String nodeId, String nodeName, Object outputData) {
        send("/topic/execution/" + executionId, Map.of(
                "event", "nodeFinished",
                "executionId", executionId,
                "nodeId", nodeId,
                "nodeName", nodeName,
                "data", outputData != null ? outputData : Map.of()
        ));
    }

    public void sendExecutionFinished(String executionId, String status, Object resultData) {
        send("/topic/execution/" + executionId, Map.of(
                "event", "executionFinished",
                "executionId", executionId,
                "status", status,
                "data", resultData != null ? resultData : Map.of()
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
