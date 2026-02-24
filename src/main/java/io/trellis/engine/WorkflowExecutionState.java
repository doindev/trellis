package io.trellis.engine;

import lombok.Data;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
    private final Map<String, Object> aiSuppliedData = new ConcurrentHashMap<>();
    private final AtomicInteger executionOrderCounter = new AtomicInteger(0);

    @Data
    public static class NodeExecutionMetadata {
        private String nodeId;
        private String nodeName;
        private String status;
        private Instant startedAt;
        private Instant finishedAt;
        private String errorMessage;
        private int executionOrder;

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

    public int nextExecutionOrder() {
        return executionOrderCounter.getAndIncrement();
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

    public void storeAiData(String nodeId, Object data) {
        aiSuppliedData.put(nodeId, data);
    }

    public Object getAiData(String nodeId) {
        return aiSuppliedData.get(nodeId);
    }

    public List<Map<String, Object>> collectInputForNode(String nodeId) {
        List<WorkflowGraph.Connection> incoming = graph.getIncomingConnections()
                .getOrDefault(nodeId, List.of());

        // Only collect from "main" connections — AI connections carry LangChain4j objects, not data items
        List<WorkflowGraph.Connection> mainIncoming = incoming.stream()
                .filter(c -> "main".equals(c.getType()))
                .toList();

        if (mainIncoming.isEmpty()) {
            return List.of();
        }

        // Check if the target node has multiple inputs
        boolean multiInput = mainIncoming.stream()
                .map(WorkflowGraph.Connection::getTargetInputIndex)
                .distinct().count() > 1;

        List<Map<String, Object>> combined = new ArrayList<>();
        for (WorkflowGraph.Connection conn : mainIncoming) {
            List<Map<String, Object>> sourceOutput = getNodeOutput(
                    conn.getSourceNodeId(), conn.getSourceOutputIndex());
            if (multiInput) {
                // Tag items with their target input index for multi-input nodes
                for (Map<String, Object> item : sourceOutput) {
                    Map<String, Object> tagged = new LinkedHashMap<>(item);
                    tagged.put("_inputIndex", conn.getTargetInputIndex());
                    combined.add(tagged);
                }
            } else {
                combined.addAll(sourceOutput);
            }
        }
        return combined;
    }

    public Map<String, Object> getOrCreateNodeContext(String nodeId) {
        return nodeContextData.computeIfAbsent(nodeId, k -> new ConcurrentHashMap<>());
    }

    /**
     * Serialize the current execution state to a checkpoint map for persistence.
     */
    public Map<String, Object> toCheckpoint() {
        Map<String, Object> checkpoint = new LinkedHashMap<>();

        // Deep-copy nodeOutputs: Map<String, List<List<Map>>>
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map.Entry<String, List<List<Map<String, Object>>>> entry : nodeOutputs.entrySet()) {
            List<List<Map<String, Object>>> outputLists = new ArrayList<>();
            for (List<Map<String, Object>> list : entry.getValue()) {
                outputLists.add(new ArrayList<>(list));
            }
            outputs.put(entry.getKey(), outputLists);
        }
        checkpoint.put("nodeOutputs", outputs);

        // Deep-copy nodeInputs: Map<String, List<Map>>
        Map<String, Object> inputs = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : nodeInputs.entrySet()) {
            inputs.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        checkpoint.put("nodeInputs", inputs);

        // Metadata: serialize to maps
        Map<String, Object> metaMap = new LinkedHashMap<>();
        for (Map.Entry<String, NodeExecutionMetadata> entry : nodeMetadata.entrySet()) {
            NodeExecutionMetadata meta = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nodeId", meta.getNodeId());
            m.put("nodeName", meta.getNodeName());
            m.put("status", meta.getStatus());
            m.put("startedAt", meta.getStartedAt() != null ? meta.getStartedAt().toString() : null);
            m.put("finishedAt", meta.getFinishedAt() != null ? meta.getFinishedAt().toString() : null);
            m.put("errorMessage", meta.getErrorMessage());
            m.put("executionOrder", meta.getExecutionOrder());
            metaMap.put(entry.getKey(), m);
        }
        checkpoint.put("nodeMetadata", metaMap);
        checkpoint.put("workflowStaticData", new LinkedHashMap<>(workflowStaticData));
        checkpoint.put("nodeContextData", new LinkedHashMap<>(nodeContextData));

        return checkpoint;
    }

    /**
     * Restore execution state from a checkpoint map.
     */
    @SuppressWarnings("unchecked")
    public static WorkflowExecutionState fromCheckpoint(
            String executionId, String workflowId,
            WorkflowGraph graph, Map<String, Object> checkpoint) {

        WorkflowExecutionState state = new WorkflowExecutionState(executionId, workflowId, graph);

        Map<String, Object> outputs = (Map<String, Object>) checkpoint.get("nodeOutputs");
        if (outputs != null) {
            for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                state.nodeOutputs.put(entry.getKey(), (List<List<Map<String, Object>>>) entry.getValue());
            }
        }

        Map<String, Object> inputs = (Map<String, Object>) checkpoint.get("nodeInputs");
        if (inputs != null) {
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                state.nodeInputs.put(entry.getKey(), (List<Map<String, Object>>) entry.getValue());
            }
        }

        Map<String, Object> metaMap = (Map<String, Object>) checkpoint.get("nodeMetadata");
        if (metaMap != null) {
            for (Map.Entry<String, Object> entry : metaMap.entrySet()) {
                Map<String, Object> m = (Map<String, Object>) entry.getValue();
                NodeExecutionMetadata meta = new NodeExecutionMetadata();
                meta.setNodeId((String) m.get("nodeId"));
                meta.setNodeName((String) m.get("nodeName"));
                meta.setStatus((String) m.get("status"));
                if (m.get("startedAt") != null) meta.setStartedAt(Instant.parse((String) m.get("startedAt")));
                if (m.get("finishedAt") != null) meta.setFinishedAt(Instant.parse((String) m.get("finishedAt")));
                meta.setErrorMessage((String) m.get("errorMessage"));
                if (m.get("executionOrder") instanceof Number n) {
                    meta.setExecutionOrder(n.intValue());
                }
                state.nodeMetadata.put(entry.getKey(), meta);
            }
        }

        Map<String, Object> staticData = (Map<String, Object>) checkpoint.get("workflowStaticData");
        if (staticData != null) {
            state.workflowStaticData.putAll(staticData);
        }

        Map<String, Map<String, Object>> contextData =
                (Map<String, Map<String, Object>>) checkpoint.get("nodeContextData");
        if (contextData != null) {
            state.nodeContextData.putAll(contextData);
        }

        return state;
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
            nodeResult.put("executionOrder", meta.getExecutionOrder());
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

            runData.put(nodeId, List.of(nodeResult));
        }

        resultData.put("runData", runData);
        return resultData;
    }
}
