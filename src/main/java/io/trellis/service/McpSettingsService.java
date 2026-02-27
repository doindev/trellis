package io.trellis.service;

import io.trellis.dto.McpClientSession;
import io.trellis.dto.McpEndpointDto;
import io.trellis.dto.McpSettingsDto;
import io.trellis.entity.McpEndpointEntity;
import io.trellis.entity.McpSettingsEntity;
import io.trellis.entity.TagEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.exception.BadRequestException;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.McpEndpointRepository;
import io.trellis.repository.McpSettingsRepository;
import io.trellis.repository.ProjectRepository;
import io.trellis.repository.TagRepository;
import io.trellis.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class McpSettingsService {

    private final McpSettingsRepository repository;
    private final WorkflowRepository workflowRepository;
    private final McpEndpointRepository endpointRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final TrellisMcpServerManager mcpServerManager;

    @Value("${server.port:5678}")
    private int serverPort;

    public McpSettingsDto getSettings() {
        boolean enabled = repository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::isEnabled)
                .orElse(false);
        List<McpEndpointDto> endpoints = endpointRepository.findAll().stream()
                .map(this::toEndpointDto)
                .toList();
        return McpSettingsDto.builder()
                .enabled(enabled)
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

    // --- Endpoint CRUD ---

    public List<McpEndpointDto> listEndpoints() {
        return endpointRepository.findAll().stream()
                .map(this::toEndpointDto)
                .toList();
    }

    @Transactional
    public McpEndpointDto createEndpoint(McpEndpointDto dto) {
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
                        io.trellis.entity.ProjectEntity::getId,
                        io.trellis.entity.ProjectEntity::getName));

        return workflowRepository.findAll().stream()
                .map(wf -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", wf.getId());
                    map.put("name", wf.getName());
                    map.put("description", wf.getDescription());
                    map.put("mcpEnabled", wf.isMcpEnabled());
                    map.put("mcpDescription", wf.getMcpDescription());
                    map.put("projectId", wf.getProjectId());
                    map.put("published", wf.isPublished());
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

    // --- Clients ---

    public List<McpClientSession> getClientSessions() {
        return mcpServerManager.getClientSessions();
    }

    // --- Helpers ---

    private McpEndpointDto toEndpointDto(McpEndpointEntity entity) {
        return McpEndpointDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .transport(entity.getTransport())
                .path(entity.getPath())
                .url("http://localhost:" + serverPort + "/mcp/" + entity.getPath())
                .enabled(entity.isEnabled())
                .build();
    }
}
