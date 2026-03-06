package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import io.cwc.entity.WorkflowEntity;
import io.cwc.repository.WorkflowRepository;

import java.util.*;

/**
 * Resolves the full configuration of a predefined agent from its workflow definition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConfigService {

    private final WorkflowRepository workflowRepository;

    /**
     * Load the agent configuration from a workflow (type=AGENT).
     * Returns null if the workflow doesn't exist or has no AI Agent node.
     */
    @SuppressWarnings("unchecked")
    public AgentConfig loadAgentConfig(String agentWorkflowId) {
        WorkflowEntity entity = workflowRepository.findById(agentWorkflowId).orElse(null);
        if (entity == null || !"AGENT".equals(entity.getType())) {
            return null;
        }

        Object nodesObj = entity.getNodes();
        Object connectionsObj = entity.getConnections();
        if (!(nodesObj instanceof List<?> nodeList)) return null;

        // Find the aiAgent node
        Map<String, Object> agentNode = null;
        for (Object item : nodeList) {
            if (item instanceof Map<?, ?> nodeMap) {
                if ("aiAgent".equals(nodeMap.get("type"))) {
                    agentNode = (Map<String, Object>) nodeMap;
                    break;
                }
            }
        }
        if (agentNode == null) return null;

        // Extract parameters
        Map<String, Object> params = agentNode.get("parameters") instanceof Map<?, ?>
                ? (Map<String, Object>) agentNode.get("parameters")
                : Map.of();
        String systemMessage = params.getOrDefault("systemMessage", "You are a helpful assistant.").toString();
        String prompt = params.getOrDefault("prompt", "{{input}}").toString();
        int maxIterations = 10;
        Object maxIter = params.get("maxIterations");
        if (maxIter instanceof Number n) maxIterations = n.intValue();

        String agentNodeId = (String) agentNode.get("id");

        // Parse connections to find connected model, memory, tools
        Map<String, Object> connections = connectionsObj instanceof Map<?, ?>
                ? (Map<String, Object>) connectionsObj
                : Map.of();

        Map<String, Object> modelNodeConfig = null;
        Map<String, Object> memoryNodeConfig = null;
        List<Map<String, Object>> toolNodeConfigs = new ArrayList<>();

        // Build a map of nodeId -> node for quick lookup
        Map<String, Map<String, Object>> nodeById = new HashMap<>();
        for (Object item : nodeList) {
            if (item instanceof Map<?, ?> nodeMap) {
                String id = (String) ((Map<String, Object>) nodeMap).get("id");
                if (id != null) nodeById.put(id, (Map<String, Object>) nodeMap);
            }
        }

        // Find nodes connected TO the agent node via AI inputs
        // Connection format: connections[sourceNodeId][connectionType][outputIndex] = [{node: targetNodeId, type, index}]
        for (Map.Entry<String, Object> sourceEntry : connections.entrySet()) {
            String sourceNodeId = sourceEntry.getKey();
            if (!(sourceEntry.getValue() instanceof Map<?, ?> typeMap)) continue;

            for (Map.Entry<?, ?> typeEntry : typeMap.entrySet()) {
                String connectionType = typeEntry.getKey().toString();
                if (!(typeEntry.getValue() instanceof List<?> outputs)) continue;

                for (Object output : outputs) {
                    if (!(output instanceof List<?> targets)) continue;
                    for (Object target : targets) {
                        if (!(target instanceof Map<?, ?> targetMap)) continue;
                        String targetNodeId = ((Map<String, Object>) targetMap).get("node").toString();
                        if (!agentNodeId.equals(targetNodeId)) continue;

                        // This source node connects to our agent
                        Map<String, Object> sourceNode = nodeById.get(sourceNodeId);
                        if (sourceNode == null) continue;

                        Map<String, Object> nodeConfig = new HashMap<>();
                        nodeConfig.put("type", sourceNode.get("type"));
                        nodeConfig.put("parameters", sourceNode.get("parameters"));
                        nodeConfig.put("credentials", sourceNode.get("credentials"));

                        switch (connectionType) {
                            case "ai_languageModel" -> modelNodeConfig = nodeConfig;
                            case "ai_memory" -> memoryNodeConfig = nodeConfig;
                            case "ai_tool" -> toolNodeConfigs.add(nodeConfig);
                        }
                    }
                }
            }
        }

        return new AgentConfig(systemMessage, prompt, maxIterations,
                modelNodeConfig, memoryNodeConfig, toolNodeConfigs);
    }

    public record AgentConfig(
            String systemMessage,
            String prompt,
            int maxIterations,
            Map<String, Object> modelNodeConfig,
            Map<String, Object> memoryNodeConfig,
            List<Map<String, Object>> toolNodeConfigs
    ) {}
}
