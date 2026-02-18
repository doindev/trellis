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

                Object mainObj = nodeConnections.get("main");
                if (!(mainObj instanceof List)) continue;

                List<?> mainList = (List<?>) mainObj;
                for (int sourceOutputIndex = 0; sourceOutputIndex < mainList.size(); sourceOutputIndex++) {
                    Object outputObj = mainList.get(sourceOutputIndex);
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
                                .build();

                        graph.connections.add(conn);
                        graph.outgoingConnections.computeIfAbsent(sourceNodeId, k -> new ArrayList<>()).add(conn);
                        graph.incomingConnections.computeIfAbsent(targetNodeId, k -> new ArrayList<>()).add(conn);
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
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            order.add(nodeId);

            List<Connection> outgoing = outgoingConnections.getOrDefault(nodeId, List.of());
            for (Connection conn : outgoing) {
                int newDegree = inDegree.merge(conn.getTargetNodeId(), -1, Integer::sum);
                if (newDegree == 0) {
                    queue.add(conn.getTargetNodeId());
                }
            }
        }

        return order;
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
}
