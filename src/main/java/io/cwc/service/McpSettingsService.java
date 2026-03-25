package io.cwc.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.cwc.dto.McpClientSession;
import io.cwc.dto.McpEndpointDto;
import io.cwc.dto.McpServerInfo;
import io.cwc.dto.McpSettingsDto;
import io.cwc.entity.McpEndpointEntity;
import io.cwc.entity.McpSettingsEntity;
import io.cwc.entity.TagEntity;
import io.cwc.entity.WorkflowEntity;
import io.cwc.exception.BadRequestException;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.McpEndpointRepository;
import io.cwc.repository.McpSettingsRepository;
import io.cwc.repository.ProjectRepository;
import io.cwc.repository.TagRepository;
import io.cwc.repository.WorkflowRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class McpSettingsService {

    private final McpSettingsRepository repository;
    private final WorkflowRepository workflowRepository;
    private final McpEndpointRepository endpointRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final CwcMcpServerManager mcpServerManager;

    @Value("${server.port:5678}")
    private int serverPort;

    public McpSettingsDto getSettings() {
        McpSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc().orElse(null);
        boolean enabled = entity != null && entity.isEnabled();
        boolean agentToolsEnabled = entity != null && entity.isAgentToolsEnabled();
        boolean agentToolsDedicated = entity == null || entity.isAgentToolsDedicated();
        String agentToolsPath = entity != null ? entity.getAgentToolsPath() : "agent";
        String agentToolsTransport = entity != null ? entity.getAgentToolsTransport() : "STREAMABLE_HTTP";
        List<McpEndpointDto> endpoints = endpointRepository.findByProjectIdIsNull().stream()
                .map(this::toEndpointDto)
                .toList();
        return McpSettingsDto.builder()
                .enabled(enabled)
                .agentToolsEnabled(agentToolsEnabled)
                .agentToolsDedicated(agentToolsDedicated)
                .agentToolsPath(agentToolsPath)
                .agentToolsTransport(agentToolsTransport)
                .agentToolsUrl(resolveBaseUrl() + "/mcp/" + agentToolsPath)
                .endpoints(endpoints)
                .build();
    }

    @Transactional
    public McpSettingsDto setEnabled(boolean enabled) {
        McpSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(McpSettingsEntity::new);
        entity.setEnabled(enabled);
        repository.save(entity);

        if (enabled) {
            mcpServerManager.startAll();
        } else {
            mcpServerManager.stopAll();
        }

        return getSettings();
    }

    @Transactional
    public McpSettingsDto updateAgentToolsSettings(Boolean enabled, Boolean dedicated, String path, String transport) {
        McpSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(McpSettingsEntity::new);
        if (enabled != null) {
            entity.setAgentToolsEnabled(enabled);
        }
        if (dedicated != null) {
            entity.setAgentToolsDedicated(dedicated);
        }
        if (path != null && !path.isBlank()) {
            entity.setAgentToolsPath(path);
        }
        if (transport != null && !transport.isBlank()) {
            entity.setAgentToolsTransport(transport);
        }
        repository.save(entity);
        return getSettings();
    }

    // --- Endpoint CRUD ---

    public List<McpEndpointDto> listEndpoints() {
        return endpointRepository.findByProjectIdIsNull().stream()
                .map(this::toEndpointDto)
                .toList();
    }

    @Transactional
    public McpEndpointDto createEndpoint(McpEndpointDto dto) {
        // Only one endpoint per transport type at app level
        endpointRepository.findByProjectIdIsNullAndTransport(dto.getTransport()).ifPresent(existing -> {
            throw new BadRequestException("An endpoint with transport '" + dto.getTransport() + "' already exists");
        });

        McpEndpointEntity entity = McpEndpointEntity.builder()
                .name(dto.getName())
                .transport(dto.getTransport())
                .path(dto.getPath())
                .enabled(true)
                .build();
        entity = endpointRepository.save(entity);
        return toEndpointDto(entity);
    }

    @Transactional
    public McpEndpointDto updateEndpoint(String id, McpEndpointDto dto) {
        McpEndpointEntity entity = endpointRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Endpoint not found: " + id));

        // If transport is changing, check uniqueness at app level
        if (dto.getTransport() != null && !dto.getTransport().equals(entity.getTransport())) {
            endpointRepository.findByProjectIdIsNullAndTransport(dto.getTransport()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new BadRequestException("An endpoint with transport '" + dto.getTransport() + "' already exists");
                }
            });
        }

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getTransport() != null) entity.setTransport(dto.getTransport());
        if (dto.getPath() != null) entity.setPath(dto.getPath());
        entity.setEnabled(dto.isEnabled());
        entity = endpointRepository.save(entity);
        return toEndpointDto(entity);
    }

    @Transactional
    public void deleteEndpoint(String id) {
        endpointRepository.deleteById(id);
    }

    // --- Workflows ---

    public List<Map<String, Object>> getAllWorkflowsWithMcpStatus() {
        Map<String, String> projectNames = projectRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        io.cwc.entity.ProjectEntity::getId,
                        io.cwc.entity.ProjectEntity::getName));

        return workflowRepository.findAll().stream()
                .map(wf -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", wf.getId());
                    map.put("name", wf.getName());
                    map.put("type", wf.getType());
                    map.put("description", wf.getDescription());
                    map.put("mcpEnabled", wf.isMcpEnabled());
                    map.put("mcpDescription", wf.getMcpDescription());
                    map.put("mcpInputSchema", wf.getMcpInputSchema());
                    map.put("mcpOutputSchema", wf.getMcpOutputSchema());
                    map.put("projectId", wf.getProjectId());
                    map.put("published", wf.isPublished());
                    map.put("hasWebhookNode", hasWebhookNode(wf));
                    map.put("projectName", wf.getProjectId() != null
                            ? projectNames.getOrDefault(wf.getProjectId(), null)
                            : null);
                    return map;
                })
                .toList();
    }

    @Transactional
    public void setWorkflowMcpEnabled(String workflowId, boolean enabled) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        if (enabled && !workflow.isPublished()) {
            throw new BadRequestException("Only published workflows can be enabled for MCP access");
        }
        if (enabled && !hasWebhookNode(workflow)) {
            throw new BadRequestException("Workflow must contain at least one Webhook node to enable MCP access");
        }
        workflow.setMcpEnabled(enabled);

        TagEntity mcpTag = tagRepository.findByName("mcp")
                .orElseGet(() -> tagRepository.save(TagEntity.builder().name("mcp").build()));
        if (enabled) {
            workflow.getTags().add(mcpTag);
        } else {
            workflow.getTags().remove(mcpTag);
        }

        workflowRepository.save(workflow);
        mcpServerManager.refreshTools();
    }

    @Transactional
    public void updateWorkflowMcpDescription(String workflowId, String mcpDescription) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        workflow.setMcpDescription(mcpDescription);
        workflowRepository.save(workflow);
        mcpServerManager.refreshTools();
    }

    @Transactional
    public void updateWorkflowMcpInputSchema(String workflowId, Object schema) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        workflow.setMcpInputSchema(schema);
        workflowRepository.save(workflow);
        mcpServerManager.refreshTools();
    }

    @Transactional
    public void updateWorkflowMcpOutputSchema(String workflowId, Object schema) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        workflow.setMcpOutputSchema(schema);
        workflowRepository.save(workflow);
        mcpServerManager.refreshTools();
    }

    public List<Map<String, Object>> autoDetectParameters(String workflowId) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));

        Object nodes = workflow.getNodes();
        if (!(nodes instanceof List<?> nodeList)) {
            return List.of();
        }

        // Patterns to find $json.fieldName and $json["fieldName"] references
        Pattern dotPattern = Pattern.compile("\\$json\\.([a-zA-Z_]\\w*)");
        Pattern bracketPattern = Pattern.compile("\\$json\\[\"([^\"]+)\"\\]");

        LinkedHashMap<String, Map<String, Object>> found = new LinkedHashMap<>();

        for (Object node : nodeList) {
            if (!(node instanceof Map<?, ?> nodeMap)) continue;
            // Skip webhook nodes — those are the trigger, not downstream consumers
            if ("webhook".equals(nodeMap.get("type"))) continue;

            String nodeJson = nodeMap.toString();
            extractFieldNames(nodeJson, dotPattern, found);
            extractFieldNames(nodeJson, bracketPattern, found);
        }

        return new ArrayList<>(found.values());
    }

    private void extractFieldNames(String text, Pattern pattern, LinkedHashMap<String, Map<String, Object>> found) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String fieldName = matcher.group(1);
            if (!found.containsKey(fieldName)) {
                Map<String, Object> param = new LinkedHashMap<>();
                param.put("name", fieldName);
                param.put("type", "string");
                param.put("description", AUTO_DESCRIPTIONS.getOrDefault(fieldName, generateDescription(fieldName)));
                param.put("required", true);
                found.put(fieldName, param);
            }
        }
    }

    private static String generateDescription(String name) {
        // Convert camelCase to spaced words: "firstName" -> "The first name"
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase();
        return "The " + spaced;
    }

    private static final LinkedHashMap<String, String> AUTO_DESCRIPTIONS = new LinkedHashMap<>();
    static {
        AUTO_DESCRIPTIONS.put("query", "The search query to execute");
        AUTO_DESCRIPTIONS.put("url", "The URL to process");
        AUTO_DESCRIPTIONS.put("email", "An email address");
        AUTO_DESCRIPTIONS.put("message", "The message content");
        AUTO_DESCRIPTIONS.put("name", "The name");
        AUTO_DESCRIPTIONS.put("id", "The unique identifier");
        AUTO_DESCRIPTIONS.put("limit", "Maximum number of results to return");
        AUTO_DESCRIPTIONS.put("offset", "Number of results to skip");
        AUTO_DESCRIPTIONS.put("filter", "Filter criteria");
        AUTO_DESCRIPTIONS.put("prompt", "The prompt text");
        AUTO_DESCRIPTIONS.put("text", "The text content");
        AUTO_DESCRIPTIONS.put("content", "The content");
        AUTO_DESCRIPTIONS.put("title", "The title");
        AUTO_DESCRIPTIONS.put("body", "The body content");
        AUTO_DESCRIPTIONS.put("subject", "The subject line");
        AUTO_DESCRIPTIONS.put("description", "A description");
        AUTO_DESCRIPTIONS.put("input", "The input data");
        AUTO_DESCRIPTIONS.put("output", "The output data");
        AUTO_DESCRIPTIONS.put("key", "The key");
        AUTO_DESCRIPTIONS.put("value", "The value");
        AUTO_DESCRIPTIONS.put("token", "The authentication token");
        AUTO_DESCRIPTIONS.put("password", "The password");
        AUTO_DESCRIPTIONS.put("username", "The username");
        AUTO_DESCRIPTIONS.put("firstName", "The first name");
        AUTO_DESCRIPTIONS.put("lastName", "The last name");
        AUTO_DESCRIPTIONS.put("phone", "The phone number");
        AUTO_DESCRIPTIONS.put("address", "The address");
        AUTO_DESCRIPTIONS.put("city", "The city");
        AUTO_DESCRIPTIONS.put("country", "The country");
        AUTO_DESCRIPTIONS.put("status", "The status");
        AUTO_DESCRIPTIONS.put("type", "The type");
        AUTO_DESCRIPTIONS.put("category", "The category");
        AUTO_DESCRIPTIONS.put("tag", "The tag");
        AUTO_DESCRIPTIONS.put("tags", "The tags");
        AUTO_DESCRIPTIONS.put("date", "The date");
        AUTO_DESCRIPTIONS.put("startDate", "The start date");
        AUTO_DESCRIPTIONS.put("endDate", "The end date");
        AUTO_DESCRIPTIONS.put("page", "The page number");
        AUTO_DESCRIPTIONS.put("pageSize", "The number of items per page");
        AUTO_DESCRIPTIONS.put("sort", "The sort criteria");
        AUTO_DESCRIPTIONS.put("order", "The sort order");
        AUTO_DESCRIPTIONS.put("format", "The format");
        AUTO_DESCRIPTIONS.put("language", "The language");
        AUTO_DESCRIPTIONS.put("source", "The source");
        AUTO_DESCRIPTIONS.put("target", "The target");
    }

    // --- MCP Servers overview ---

    public List<McpServerInfo> getMcpServers() {
        List<McpEndpointEntity> allEndpoints = endpointRepository.findAll();

        List<McpEndpointEntity> instanceEndpoints = allEndpoints.stream()
                .filter(e -> e.getProjectId() == null)
                .toList();

        Map<String, List<McpEndpointEntity>> projectEndpointMap = allEndpoints.stream()
                .filter(e -> e.getProjectId() != null && e.isEnabled())
                .collect(java.util.stream.Collectors.groupingBy(McpEndpointEntity::getProjectId));

        // Client counts by endpoint
        List<McpClientSession> clients = mcpServerManager.getClientSessions();
        Map<String, Long> connectedByEndpoint = clients.stream()
                .filter(c -> c.getDisconnectedAt() == null)
                .collect(java.util.stream.Collectors.groupingBy(
                        c -> c.getEndpointId() != null ? c.getEndpointId() : "",
                        java.util.stream.Collectors.counting()));

        java.util.Set<String> instanceEndpointIds = instanceEndpoints.stream()
                .map(McpEndpointEntity::getId)
                .collect(java.util.stream.Collectors.toSet());

        long instanceClientCount = connectedByEndpoint.entrySet().stream()
                .filter(e -> instanceEndpointIds.contains(e.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();

        List<McpServerInfo> servers = new ArrayList<>();

        // Instance-level server
        servers.add(McpServerInfo.builder()
                .id("instance")
                .name("Instance-level")
                .projectId(null)
                .endpoints(instanceEndpoints.stream().map(this::toEndpointDto).toList())
                .connectedClients((int) instanceClientCount)
                .build());

        // Project servers
        Map<String, String> projectNames = projectRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(
                        io.cwc.entity.ProjectEntity::getId,
                        io.cwc.entity.ProjectEntity::getName));

        for (var entry : projectEndpointMap.entrySet()) {
            String projId = entry.getKey();
            java.util.Set<String> projEndpointIds = entry.getValue().stream()
                    .map(McpEndpointEntity::getId)
                    .collect(java.util.stream.Collectors.toSet());

            long projClientCount = connectedByEndpoint.entrySet().stream()
                    .filter(e -> projEndpointIds.contains(e.getKey()))
                    .mapToLong(Map.Entry::getValue)
                    .sum();

            servers.add(McpServerInfo.builder()
                    .id(projId)
                    .name(projectNames.getOrDefault(projId, "Unknown Project"))
                    .projectId(projId)
                    .endpoints(entry.getValue().stream().map(this::toEndpointDto).toList())
                    .connectedClients((int) projClientCount)
                    .build());
        }

        return servers;
    }

    // --- Project MCP Endpoint ---

    public List<McpEndpointDto> getProjectMcpEndpoints(String projectId) {
        return endpointRepository.findAllByProjectId(projectId).stream()
                .map(this::toEndpointDto)
                .toList();
    }

    @Transactional
    public McpEndpointDto saveProjectMcpEndpoint(String projectId, String projectName, boolean enabled, String path, String transport) {
        final String resolvedTransport = (transport == null || transport.isBlank()) ? "STREAMABLE_HTTP" : transport;

        if (!enabled) {
            endpointRepository.findByProjectIdAndTransport(projectId, resolvedTransport).ifPresent(existing -> {
                endpointRepository.delete(existing);
            });
            mcpServerManager.refreshTools();
            return null;
        }

        // Build the full endpoint path: {contextPath} or {contextPath}/{userPath}
        String contextPath = projectRepository.findById(projectId)
                .map(p -> p.getContextPath())
                .orElse(null);
        if (contextPath == null || contextPath.isBlank()) {
            throw new BadRequestException("Project must have a context path configured to enable MCP");
        }
        String userPath = (path != null) ? path.trim() : "";
        String fullPath = userPath.isEmpty() ? contextPath : contextPath + "/" + userPath;

        // Validate path uniqueness (excluding self)
        endpointRepository.findByPath(fullPath).ifPresent(existing -> {
            boolean isSelf = projectId.equals(existing.getProjectId())
                    && resolvedTransport.equals(existing.getTransport());
            if (!isSelf) {
                throw new BadRequestException("Path '" + fullPath + "' is already in use by another endpoint");
            }
        });

        // Reject reserved agent tools path
        McpSettingsEntity settings = repository.findFirstByOrderByCreatedAtAsc().orElse(null);
        if (settings != null && fullPath.equals(settings.getAgentToolsPath())) {
            throw new BadRequestException("Path '" + fullPath + "' is reserved for agent tools");
        }

        McpEndpointEntity entity = endpointRepository.findByProjectIdAndTransport(projectId, resolvedTransport)
                .orElseGet(() -> McpEndpointEntity.builder()
                        .projectId(projectId)
                        .name(projectName != null ? projectName : "Project MCP")
                        .transport(resolvedTransport)
                        .path(fullPath)
                        .enabled(true)
                        .build());

        entity.setPath(fullPath);
        entity.setName(projectName != null ? projectName : entity.getName());
        entity.setEnabled(true);

        entity = endpointRepository.save(entity);
        mcpServerManager.refreshTools();
        return toEndpointDto(entity);
    }

    // --- Clients ---

    public List<McpClientSession> getClientSessions() {
        return mcpServerManager.getClientSessions();
    }

    // --- Helpers ---

    private String resolveBaseUrl() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String scheme = request.getScheme();
            String host = request.getHeader("Host");
            if (host == null) host = request.getServerName() + ":" + request.getServerPort();
            return scheme + "://" + host;
        }
        return "http://localhost:" + serverPort;
    }

    private boolean hasWebhookNode(WorkflowEntity workflow) {
        Object nodes = workflow.getNodes();
        if (nodes instanceof List<?> nodeList) {
            return nodeList.stream().anyMatch(n -> {
                if (n instanceof Map<?, ?> map) {
                    return "webhook".equals(map.get("type"));
                }
                return false;
            });
        }
        return false;
    }

    private McpEndpointDto toEndpointDto(McpEndpointEntity entity) {
        return McpEndpointDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .transport(entity.getTransport())
                .path(entity.getPath())
                .url(resolveBaseUrl() + "/mcp/" + entity.getPath())
                .enabled(entity.isEnabled())
                .build();
    }
}
