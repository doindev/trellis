package io.trellis.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
@Data
public class WorkflowGraph {

    private final Map<String, WorkflowNode> nodes = new LinkedHashMap<>();
    private final List<Connection> connections = new ArrayList<>();
    private final Map<String, List<Connection>> outgoingConnections = new HashMap<>();
    private final Map<String, List<Connection>> incomingConnections = new HashMap<>();

    @Data
    @Builder
    public static class WorkflowNode {
        private String id;
        private String name;
        private String type;
        private int typeVersion;
        private Map<String, Object> parameters;
        private double[] position;
        private boolean disabled;
        private Map<String, Object> credentials;
        private boolean continueOnFail;
    }

    @Data
    @Builder
    public static class Connection {
        private String sourceNodeId;
        private int sourceOutputIndex;
        private String targetNodeId;
        private int targetInputIndex;
        @Builder.Default
        private String type = "main";
    }

    @SuppressWarnings("unchecked")
    public static WorkflowGraph parse(Object nodesObj, Object connectionsObj, ObjectMapper objectMapper) {
        WorkflowGraph graph = new WorkflowGraph();

        List<Map<String, Object>> nodeList;
        if (nodesObj instanceof List) {
            nodeList = (List<Map<String, Object>>) nodesObj;
        } else if (nodesObj instanceof String) {
            try {
                nodeList = objectMapper.readValue((String) nodesObj,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                log.error("Failed to parse nodes JSON", e);
                return graph;
            }
        } else {
            return graph;
        }

        for (Map<String, Object> nodeMap : nodeList) {
            String id = (String) nodeMap.get("id");
            WorkflowNode node = WorkflowNode.builder()
                    .id(id)
                    .name((String) nodeMap.getOrDefault("name", id))
                    .type((String) nodeMap.get("type"))
                    .typeVersion(nodeMap.containsKey("typeVersion") ?
                            ((Number) nodeMap.get("typeVersion")).intValue() : 1)
                    .parameters((Map<String, Object>) nodeMap.getOrDefault("parameters", Map.of()))
                    .disabled(Boolean.TRUE.equals(nodeMap.get("disabled")))
                    .credentials((Map<String, Object>) nodeMap.get("credentials"))
                    .continueOnFail(Boolean.TRUE.equals(nodeMap.get("continueOnFail")))
                    .build();

            if (nodeMap.get("position") instanceof List) {
                List<Number> pos = (List<Number>) nodeMap.get("position");
                if (pos.size() >= 2) {
                    node.setPosition(new double[]{pos.get(0).doubleValue(), pos.get(1).doubleValue()});
                }
            }

            graph.nodes.put(id, node);
        }

        Map<String, Object> connMap;
        if (connectionsObj instanceof Map) {
            connMap = (Map<String, Object>) connectionsObj;
        } else if (connectionsObj instanceof String) {
            try {
                connMap = objectMapper.readValue((String) connectionsObj, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse connections JSON", e);
                return graph;
            }
        } else {
            return graph;
        }

        if (connMap != null) {
            for (Map.Entry<String, Object> entry : connMap.entrySet()) {
                String sourceNodeId = entry.getKey();
                Map<String, Object> nodeConnections = (Map<String, Object>) entry.getValue();
                if (nodeConnections == null) continue;

                for (Map.Entry<String, Object> connTypeEntry : nodeConnections.entrySet()) {
                    String connectionType = connTypeEntry.getKey();
                    Object typeObj = connTypeEntry.getValue();
                    if (!(typeObj instanceof List)) continue;

                    List<?> outputList = (List<?>) typeObj;
                    for (int sourceOutputIndex = 0; sourceOutputIndex < outputList.size(); sourceOutputIndex++) {
                        Object outputObj = outputList.get(sourceOutputIndex);
                        if (!(outputObj instanceof List)) continue;

                        List<Map<String, Object>> targets = (List<Map<String, Object>>) outputObj;
                        for (Map<String, Object> target : targets) {
                            String targetNodeId = (String) target.get("node");
                            int targetInputIndex = target.containsKey("index") ?
                                    ((Number) target.get("index")).intValue() : 0;

                            Connection conn = Connection.builder()
                                    .sourceNodeId(sourceNodeId)
                                    .sourceOutputIndex(sourceOutputIndex)
                                    .targetNodeId(targetNodeId)
                                    .targetInputIndex(targetInputIndex)
                                    .type(connectionType)
                                    .build();

                            graph.connections.add(conn);
                            graph.outgoingConnections.computeIfAbsent(sourceNodeId, k -> new ArrayList<>()).add(conn);
                            graph.incomingConnections.computeIfAbsent(targetNodeId, k -> new ArrayList<>()).add(conn);
                        }
                    }
                }
            }
        }

        return graph;
    }

    public List<String> getTopologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        for (String nodeId : nodes.keySet()) {
            inDegree.put(nodeId, 0);
        }
        for (Connection conn : connections) {
            inDegree.merge(conn.getTargetNodeId(), 1, Integer::sum);
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        Set<String> processed = new HashSet<>();

        while (!queue.isEmpty() || processed.size() < nodes.size()) {
            if (queue.isEmpty()) {
                // Remaining nodes are in cycles. Break the cycle by forcing
                // a cycle node into the queue, preferring loopOverItems nodes.
                String forced = null;
                for (String nodeId : nodes.keySet()) {
                    if (!processed.contains(nodeId)) {
                        WorkflowNode n = nodes.get(nodeId);
                        if ("loopOverItems".equals(n.getType())) {
                            forced = nodeId;
                            break;
                        }
                        if (forced == null) {
                            forced = nodeId;
                        }
                    }
                }
                if (forced == null) break;
                queue.add(forced);
            }

            String nodeId = queue.poll();
            if (processed.contains(nodeId)) continue;
            processed.add(nodeId);
            order.add(nodeId);

            List<Connection> outgoing = outgoingConnections.getOrDefault(nodeId, List.of());
            for (Connection conn : outgoing) {
                int newDegree = inDegree.merge(conn.getTargetNodeId(), -1, Integer::sum);
                if (newDegree <= 0 && !processed.contains(conn.getTargetNodeId())) {
                    queue.add(conn.getTargetNodeId());
                }
            }
        }

        return order;
    }

    /**
     * Find the nodes that form the loop body for a given loop node.
     * Traces from the loop output (outputIndex 1) through downstream nodes
     * until reaching the loop node's input again.
     */
    public List<String> findLoopBodyNodes(String loopNodeId) {
        List<String> body = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> bfs = new LinkedList<>();

        // Start from successors of the loop output (output index 1)
        for (Connection conn : getSuccessors(loopNodeId, 1)) {
            String target = conn.getTargetNodeId();
            if (!target.equals(loopNodeId) && !visited.contains(target)) {
                bfs.add(target);
                visited.add(target);
            }
        }

        while (!bfs.isEmpty()) {
            String nodeId = bfs.poll();
            body.add(nodeId);

            for (Connection conn : outgoingConnections.getOrDefault(nodeId, List.of())) {
                String target = conn.getTargetNodeId();
                // Stop when we reach back to the loop node
                if (target.equals(loopNodeId)) continue;
                if (!visited.contains(target)) {
                    visited.add(target);
                    bfs.add(target);
                }
            }
        }

        return body;
    }

    public List<Connection> getIncomingConnectionsByType(String nodeId, String type) {
        return incomingConnections.getOrDefault(nodeId, List.of()).stream()
                .filter(c -> type.equals(c.getType()))
                .toList();
    }

    public List<Connection> getSuccessors(String nodeId, int outputIndex) {
        return outgoingConnections.getOrDefault(nodeId, List.of()).stream()
                .filter(c -> c.getSourceOutputIndex() == outputIndex)
                .toList();
    }

    public WorkflowNode getStartNode() {
        for (WorkflowNode node : nodes.values()) {
            if (incomingConnections.getOrDefault(node.getId(), List.of()).isEmpty()) {
                return node;
            }
        }
        return nodes.values().isEmpty() ? null : nodes.values().iterator().next();
    }

    /**
     * BFS from startNodeId through outgoing connections to find all reachable node IDs
     * (including the start node itself).
     */
    public Set<String> getReachableNodes(String startNodeId) {
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        reachable.add(startNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            for (Connection conn : outgoingConnections.getOrDefault(nodeId, List.of())) {
                String target = conn.getTargetNodeId();
                if (reachable.add(target)) {
                    queue.add(target);
                }
            }
        }
        return reachable;
    }
}
