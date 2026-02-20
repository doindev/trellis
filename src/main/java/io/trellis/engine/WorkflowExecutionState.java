package io.trellis.engine;

import lombok.Data;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class WorkflowExecutionState {

    private final String executionId;
    private final String workflowId;
    private final WorkflowGraph graph;
    private volatile boolean cancelled = false;

    private final Map<String, List<List<Map<String, Object>>>> nodeOutputs = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> nodeInputs = new ConcurrentHashMap<>();
    private final Map<String, NodeExecutionMetadata> nodeMetadata = new ConcurrentHashMap<>();
    private final Map<String, Object> workflowStaticData = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> nodeContextData = new ConcurrentHashMap<>();

    @Data
    public static class NodeExecutionMetadata {
        private String nodeId;
        private String nodeName;
        private String status;
        private Instant startedAt;
        private Instant finishedAt;
        private String errorMessage;

        public long getDurationMs() {
            if (startedAt == null || finishedAt == null) return 0;
            return finishedAt.toEpochMilli() - startedAt.toEpochMilli();
        }
    }

    public WorkflowExecutionState(String executionId, String workflowId, WorkflowGraph graph) {
        this.executionId = executionId;
        this.workflowId = workflowId;
        this.graph = graph;
    }

    public void storeInput(String nodeId, List<Map<String, Object>> input) {
        nodeInputs.put(nodeId, input);
    }

    public void storeOutput(String nodeId, List<List<Map<String, Object>>> output) {
        nodeOutputs.put(nodeId, output);
    }

    public List<Map<String, Object>> getNodeOutput(String nodeId, int outputIndex) {
        List<List<Map<String, Object>>> outputs = nodeOutputs.get(nodeId);
        if (outputs == null || outputIndex >= outputs.size()) {
            return List.of();
        }
        return outputs.get(outputIndex);
    }

    public List<Map<String, Object>> collectInputForNode(String nodeId) {
        List<WorkflowGraph.Connection> incoming = graph.getIncomingConnections()
                .getOrDefault(nodeId, List.of());

        if (incoming.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> combined = new ArrayList<>();
        for (WorkflowGraph.Connection conn : incoming) {
            List<Map<String, Object>> sourceOutput = getNodeOutput(
                    conn.getSourceNodeId(), conn.getSourceOutputIndex());
            combined.addAll(sourceOutput);
        }
        return combined;
    }

    public Map<String, Object> getOrCreateNodeContext(String nodeId) {
        return nodeContextData.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>());
    }

    public Map<String, Object> buildResultData() {
        Map<String, Object> resultData = new LinkedHashMap<>();
        Map<String, Object> runData = new LinkedHashMap<>();

        for (Map.Entry<String, NodeExecutionMetadata> entry : nodeMetadata.entrySet()) {
            String nodeId = entry.getKey();
            NodeExecutionMetadata meta = entry.getValue();
            Map<String, Object> nodeResult = new LinkedHashMap<>();
            nodeResult.put("startedAt", meta.getStartedAt() != null ? meta.getStartedAt().toString() : null);
            nodeResult.put("finishedAt", meta.getFinishedAt() != null ? meta.getFinishedAt().toString() : null);
            nodeResult.put("executionTime", meta.getDurationMs());
            nodeResult.put("status", meta.getStatus());
            nodeResult.put("error", meta.getErrorMessage());

            List<Map<String, Object>> inputs = nodeInputs.get(nodeId);
            if (inputs != null) {
                nodeResult.put("inputData", Map.of("main", List.of(inputs)));
            }

            List<List<Map<String, Object>>> outputs = nodeOutputs.get(nodeId);
            if (outputs != null) {
                nodeResult.put("data", Map.of("main", outputs));
            }

            runData.put(meta.getNodeName() != null ? meta.getNodeName() : nodeId, List.of(nodeResult));
        }

        resultData.put("runData", runData);
        return resultData;
    }
}
