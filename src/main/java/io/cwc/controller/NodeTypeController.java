package io.cwc.controller;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.bind.annotation.RequestParam;
import io.cwc.exception.NotFoundException;
import io.cwc.nodes.core.NodeRegistry;
import io.cwc.nodes.core.NodeRegistry.NodeRegistration;
import io.cwc.service.WorkflowService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/node-types")
@RequiredArgsConstructor
public class NodeTypeController {

    private final NodeRegistry nodeRegistry;
    private final WorkflowService workflowService;

    @GetMapping
    public Collection<Map<String, Object>> getAll() {
        return nodeRegistry.getAllNodes().stream()
                .map(this::toMap)
                .toList();
    }

    @GetMapping("/{type}")
    public Map<String, Object> getByType(@PathVariable String type) {
        NodeRegistration registration = nodeRegistry.getNode(type)
                .orElseThrow(() -> new NotFoundException("Node type not found: " + type));
        return toMap(registration);
    }

    @GetMapping("/agent-options")
    public List<Map<String, String>> getAgentOptions(@RequestParam(required = false) String projectId) {
        var agents = projectId != null
                ? workflowService.listAgentsVisibleToProject(projectId)
                : workflowService.listWorkflows().stream()
                        .filter(w -> "AGENT".equals(w.getType()))
                        .toList();
        return agents.stream()
                .map(a -> Map.of("value", a.getId(), "name", a.getName()))
                .toList();
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        return nodeRegistry.getAllNodes().stream()
                .map(NodeRegistration::getCategory)
                .distinct()
                .sorted()
                .toList();
    }

    private Map<String, Object> toMap(NodeRegistration reg) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", reg.getType());
        map.put("displayName", reg.getDisplayName());
        map.put("description", reg.getDescription());
        map.put("category", reg.getCategory());
        map.put("version", reg.getVersion());
        map.put("icon", reg.getIcon());
        map.put("isTrigger", reg.isTrigger());
        map.put("isPolling", reg.isPolling());
        map.put("credentials", reg.getCredentials());
        map.put("group", reg.getGroup());
        map.put("subtitle", reg.getSubtitle());
        map.put("documentationUrl", reg.getDocumentationUrl());
        map.put("searchOnly", reg.isSearchOnly());
        map.put("triggerCategory", reg.getTriggerCategory());
        map.put("triggerFavorite", reg.isTriggerFavorite());
        map.put("parameters", reg.getParameters());
        map.put("inputs", reg.getInputs());
        map.put("outputs", reg.getOutputs());
        return map;
    }
}
