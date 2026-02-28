package io.trellis.service;

import io.trellis.dto.*;
import io.trellis.entity.TagEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.entity.WorkflowVersionEntity;
import io.trellis.exception.BadRequestException;
import io.trellis.exception.NotFoundException;
import io.trellis.nodes.core.NodeParameter;
import io.trellis.nodes.core.NodeRegistry;
import io.trellis.repository.TagRepository;
import io.trellis.repository.WorkflowRepository;
import io.trellis.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WebhookService webhookService;
    private final TagRepository tagRepository;
    private final NodeRegistry nodeRegistry;

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> listWorkflowsByProject(String projectId) {
        return workflowRepository.findByProjectId(projectId).stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse getWorkflow(String id) {
        return toResponse(findById(id));
    }

    @Transactional
    public WorkflowResponse createWorkflow(WorkflowCreateRequest request) {
        WorkflowEntity entity = WorkflowEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .projectId(request.getProjectId())
                .nodes(request.getNodes())
                .connections(request.getConnections())
                .settings(request.getSettings())
                .build();
        return toResponse(workflowRepository.save(entity));
    }

    @Transactional
    public WorkflowResponse updateWorkflow(String id, WorkflowUpdateRequest request) {
        WorkflowEntity entity = findById(id);
        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getNodes() != null) entity.setNodes(request.getNodes());
        if (request.getConnections() != null) entity.setConnections(request.getConnections());
        if (request.getSettings() != null) entity.setSettings(request.getSettings());
        if (request.getStaticData() != null) entity.setStaticData(request.getStaticData());
        if (request.getPinData() != null) entity.setPinData(request.getPinData());
        if (request.getMcpEnabled() != null) entity.setMcpEnabled(request.getMcpEnabled());
        if (request.getMcpDescription() != null) entity.setMcpDescription(request.getMcpDescription());
        if (request.getMcpInputSchema() != null) entity.setMcpInputSchema(request.getMcpInputSchema());
        if (request.getMcpOutputSchema() != null) entity.setMcpOutputSchema(request.getMcpOutputSchema());
        if (request.getSwaggerEnabled() != null) entity.setSwaggerEnabled(request.getSwaggerEnabled());

        if (entity.isPublished() && (request.getNodes() != null || request.getConnections() != null
                || request.getMcpInputSchema() != null || request.getMcpOutputSchema() != null)) {
            entity.setVersionIsDirty(true);
        }

        return toResponse(workflowRepository.save(entity));
    }

    @Transactional
    public void deleteWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
        }
        workflowVersionRepository.deleteByWorkflowId(id);
        workflowRepository.delete(entity);
    }

    @Transactional
    public WorkflowResponse publishWorkflow(String id, PublishWorkflowRequest request) {
        WorkflowEntity entity = findById(id);
        validateNodesForPublish(entity);
        int newVersion = entity.getCurrentVersion() + 1;

        String versionName = request.getVersionName();
        if (versionName == null || versionName.isBlank()) {
            versionName = "Version " + newVersion;
        }

        WorkflowVersionEntity.WorkflowVersionEntityBuilder versionBuilder = WorkflowVersionEntity.builder()
                .workflowId(id)
                .versionNumber(newVersion)
                .versionName(versionName)
                .description(request.getDescription())
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .publishedAt(Instant.now());

        if (request.isIncludePinData() && entity.getPinData() != null) {
            versionBuilder.pinData(entity.getPinData());
        }

        workflowVersionRepository.save(versionBuilder.build());

        entity.setCurrentVersion(newVersion);
        entity.setPublished(true);
        entity.setVersionIsDirty(false);
        entity = workflowRepository.save(entity);

        webhookService.registerWorkflowWebhooks(entity);
        log.info("Published workflow: {} ({}) as {}", entity.getName(), id, versionName);

        return toResponse(entity);
    }

    public List<WorkflowVersionResponse> getWorkflowVersions(String id) {
        findById(id); // verify workflow exists
        return workflowVersionRepository.findByWorkflowIdOrderByVersionNumberDesc(id).stream()
                .map(v -> WorkflowVersionResponse.builder()
                        .id(v.getId())
                        .workflowId(v.getWorkflowId())
                        .versionNumber(v.getVersionNumber())
                        .versionName(v.getVersionName())
                        .description(v.getDescription())
                        .publishedAt(v.getPublishedAt())
                        .build())
                .toList();
    }

    @Transactional
    public WorkflowResponse duplicateWorkflow(String id) {
        WorkflowEntity original = findById(id);
        WorkflowEntity duplicate = WorkflowEntity.builder()
                .name("Copy of " + original.getName())
                .description(original.getDescription())
                .projectId(original.getProjectId())
                .nodes(original.getNodes())
                .connections(original.getConnections())
                .settings(original.getSettings())
                .build();
        return toResponse(workflowRepository.save(duplicate));
    }

    @Transactional
    public WorkflowResponse unpublishWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
            entity.setPublished(false);
            disableSwaggerAccess(entity);
            entity = workflowRepository.save(entity);
            log.info("Unpublished workflow: {} ({})", entity.getName(), id);
        }
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse archiveWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        if (entity.isPublished()) {
            webhookService.deregisterWorkflowWebhooks(id);
            entity.setPublished(false);
        }
        disableSwaggerAccess(entity);
        entity.setArchived(true);
        entity = workflowRepository.save(entity);
        log.info("Archived workflow: {} ({})", entity.getName(), id);
        return toResponse(entity);
    }

    @Transactional
    public void updateStaticData(String id, Object staticData) {
        WorkflowEntity entity = findById(id);
        entity.setStaticData(staticData);
        workflowRepository.save(entity);
    }

    @Transactional
    public WorkflowResponse updateWorkflowTags(String id, List<String> tagIds) {
        WorkflowEntity entity = findById(id);
        Set<TagEntity> tags = new LinkedHashSet<>();
        for (String tagId : tagIds) {
            tags.add(tagRepository.findById(tagId)
                    .orElseThrow(() -> new NotFoundException("Tag not found: " + tagId)));
        }
        entity.setTags(tags);
        return toResponse(workflowRepository.save(entity));
    }

    private void disableSwaggerAccess(WorkflowEntity entity) {
        if (entity.isSwaggerEnabled()) {
            entity.setSwaggerEnabled(false);
            tagRepository.findByName("swagger").ifPresent(tag -> entity.getTags().remove(tag));
        }
    }

    @SuppressWarnings("unchecked")
    private void validateNodesForPublish(WorkflowEntity entity) {
        Object nodesObj = entity.getNodes();
        if (!(nodesObj instanceof List<?> nodeList) || nodeList.isEmpty()) return;

        List<String> errors = new ArrayList<>();
        for (Object item : nodeList) {
            if (!(item instanceof Map<?, ?> nodeMap)) continue;
            String nodeType = (String) nodeMap.get("type");
            String nodeName = (String) nodeMap.get("name");
            if (nodeType == null) continue;

            var reg = nodeRegistry.getNode(nodeType).orElse(null);
            if (reg == null) continue;

            // Check credentials
            Map<String, Object> credentials = (Map<String, Object>) nodeMap.get("credentials");
            for (String credType : reg.getCredentials()) {
                if (credentials == null || !credentials.containsKey(credType)
                        || credentials.get(credType) == null || credentials.get(credType).toString().isBlank()) {
                    errors.add(nodeName + ": missing credential '" + credType + "'");
                }
            }

            // Check required parameters
            Map<String, Object> params = (Map<String, Object>) nodeMap.get("parameters");
            if (params == null) params = Map.of();
            for (NodeParameter param : reg.getParameters()) {
                if (!param.isRequired()) continue;
                if (param.getDefaultValue() != null && !"".equals(param.getDefaultValue())) continue;

                // Check displayOptions.show conditions
                if (param.getDisplayOptions() != null) {
                    Object showObj = param.getDisplayOptions().get("show");
                    if (showObj instanceof Map<?, ?> showMap) {
                        boolean conditionsMet = true;
                        for (Map.Entry<?, ?> entry : showMap.entrySet()) {
                            Object currentVal = params.get(entry.getKey());
                            Object allowed = entry.getValue();
                            if (allowed instanceof List<?> allowedList) {
                                if (!allowedList.contains(currentVal)) {
                                    conditionsMet = false;
                                    break;
                                }
                            } else {
                                if (!java.util.Objects.equals(currentVal, allowed)) {
                                    conditionsMet = false;
                                    break;
                                }
                            }
                        }
                        if (!conditionsMet) continue;
                    }
                }

                Object value = params.get(param.getName());
                if (value == null || value.toString().isBlank()) {
                    errors.add(nodeName + ": missing required parameter '" + param.getDisplayName() + "'");
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException("Workflow has validation errors: " + String.join("; ", errors));
        }
    }

    public WorkflowEntity findById(String id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        List<TagResponse> tagResponses = entity.getTags() != null
                ? entity.getTags().stream()
                    .map(t -> TagResponse.builder()
                            .id(t.getId())
                            .name(t.getName())
                            .createdAt(t.getCreatedAt())
                            .updatedAt(t.getUpdatedAt())
                            .build())
                    .toList()
                : List.of();

        return WorkflowResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .description(entity.getDescription())
                .published(entity.isPublished())
                .archived(entity.isArchived())
                .currentVersion(entity.getCurrentVersion())
                .versionIsDirty(entity.isVersionIsDirty())
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .staticData(entity.getStaticData())
                .pinData(entity.getPinData())
                .mcpEnabled(entity.isMcpEnabled())
                .mcpDescription(entity.getMcpDescription())
                .mcpInputSchema(entity.getMcpInputSchema())
                .mcpOutputSchema(entity.getMcpOutputSchema())
                .swaggerEnabled(entity.isSwaggerEnabled())
                .tags(tagResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
