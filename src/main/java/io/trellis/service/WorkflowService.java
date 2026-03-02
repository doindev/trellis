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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
                .nodes(ensureNodeIds(request.getNodes()))
                .connections(request.getConnections())
                .settings(request.getSettings())
                .build();
        return toResponse(workflowRepository.save(entity));
    }

    private static final Duration SAVE_REVISION_THROTTLE = Duration.ofSeconds(30);

    @Transactional
    public WorkflowResponse updateWorkflow(String id, WorkflowUpdateRequest request) {
        WorkflowEntity entity = findById(id);
        boolean dataChanged = request.getNodes() != null || request.getConnections() != null;

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getDescription() != null) entity.setDescription(request.getDescription());
        if (request.getNodes() != null) entity.setNodes(ensureNodeIds(request.getNodes()));
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

        WorkflowEntity saved = workflowRepository.save(entity);

        // Create throttled save revision when nodes or connections change
        if (dataChanged) {
            createThrottledSaveRevision(saved);
        }

        return toResponse(saved);
    }

    private void createThrottledSaveRevision(WorkflowEntity entity) {
        var lastSave = workflowVersionRepository.findFirstByWorkflowIdAndPublishedFalseOrderByPublishedAtDesc(entity.getId());
        if (lastSave.isPresent()) {
            Duration elapsed = Duration.between(lastSave.get().getPublishedAt(), Instant.now());
            if (elapsed.compareTo(SAVE_REVISION_THROTTLE) < 0) {
                return; // too recent, skip
            }
        }

        WorkflowVersionEntity saveVersion = WorkflowVersionEntity.builder()
                .workflowId(entity.getId())
                .versionNumber(0)
                .versionName(null)
                .published(false)
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .publishedAt(Instant.now())
                .build();
        workflowVersionRepository.save(saveVersion);
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
                .published(true)
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
                .map(this::toVersionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<WorkflowVersionResponse> getWorkflowVersionsPaged(String id, int page, int size, String filter) {
        findById(id);
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<WorkflowVersionEntity> result;
        if ("published".equals(filter)) {
            result = workflowVersionRepository.findByWorkflowIdAndPublishedOrderByPublishedAtDesc(id, true, pageRequest);
        } else if ("saves".equals(filter)) {
            result = workflowVersionRepository.findByWorkflowIdAndPublishedOrderByPublishedAtDesc(id, false, pageRequest);
        } else {
            result = workflowVersionRepository.findByWorkflowIdOrderByPublishedAtDesc(id, pageRequest);
        }
        return result.map(this::toVersionResponse);
    }

    @Transactional(readOnly = true)
    public WorkflowVersionResponse getWorkflowVersion(String workflowId, String versionId) {
        findById(workflowId);
        WorkflowVersionEntity v = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!v.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }
        return WorkflowVersionResponse.builder()
                .id(v.getId())
                .workflowId(v.getWorkflowId())
                .versionNumber(v.getVersionNumber())
                .versionName(v.getVersionName())
                .description(v.getDescription())
                .published(v.isPublished())
                .publishedAt(v.getPublishedAt())
                .nodes(v.getNodes())
                .connections(v.getConnections())
                .settings(v.getSettings())
                .build();
    }

    @Transactional
    public WorkflowResponse publishFromVersion(String workflowId, String versionId) {
        WorkflowEntity entity = findById(workflowId);
        WorkflowVersionEntity version = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!version.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }

        // Update workflow entity with version's data
        entity.setNodes(version.getNodes());
        entity.setConnections(version.getConnections());
        if (version.getSettings() != null) {
            entity.setSettings(version.getSettings());
        }

        // Validate and publish
        validateNodesForPublish(entity);
        int newVersion = entity.getCurrentVersion() + 1;
        String versionName = "Version " + newVersion;

        WorkflowVersionEntity newVersionEntity = WorkflowVersionEntity.builder()
                .workflowId(workflowId)
                .versionNumber(newVersion)
                .versionName(versionName)
                .description("Published from version " + (version.isPublished() ? version.getVersionName() : "save revision"))
                .published(true)
                .nodes(version.getNodes())
                .connections(version.getConnections())
                .settings(version.getSettings())
                .publishedAt(Instant.now())
                .build();
        workflowVersionRepository.save(newVersionEntity);

        entity.setCurrentVersion(newVersion);
        entity.setPublished(true);
        entity.setVersionIsDirty(false);
        entity = workflowRepository.save(entity);

        webhookService.registerWorkflowWebhooks(entity);
        log.info("Published workflow {} ({}) from version {} as {}", entity.getName(), workflowId, versionId, versionName);
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse cloneFromVersion(String workflowId, String versionId) {
        WorkflowEntity original = findById(workflowId);
        WorkflowVersionEntity version = workflowVersionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version not found: " + versionId));
        if (!version.getWorkflowId().equals(workflowId)) {
            throw new NotFoundException("Version " + versionId + " does not belong to workflow " + workflowId);
        }

        WorkflowEntity clone = WorkflowEntity.builder()
                .name("Copy of " + original.getName())
                .description(original.getDescription())
                .projectId(original.getProjectId())
                .nodes(version.getNodes())
                .connections(version.getConnections())
                .settings(version.getSettings())
                .build();
        return toResponse(workflowRepository.save(clone));
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

    /**
     * Ensures every node in the list has an "id" field. If a node is missing "id",
     * its "name" is used as the id (matching name-based connection references).
     */
    @SuppressWarnings("unchecked")
    private Object ensureNodeIds(Object nodesObj) {
        if (!(nodesObj instanceof List<?> nodeList)) return nodesObj;
        for (Object item : nodeList) {
            if (item instanceof Map<?, ?> nodeMap) {
                Map<String, Object> mutable = (Map<String, Object>) nodeMap;
                if (mutable.get("id") == null || mutable.get("id").toString().isBlank()) {
                    String name = (String) mutable.get("name");
                    if (name != null) {
                        mutable.put("id", name);
                    }
                }
            }
        }
        return nodesObj;
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

    private WorkflowVersionResponse toVersionResponse(WorkflowVersionEntity v) {
        return WorkflowVersionResponse.builder()
                .id(v.getId())
                .workflowId(v.getWorkflowId())
                .versionNumber(v.getVersionNumber())
                .versionName(v.getVersionName())
                .description(v.getDescription())
                .published(v.isPublished())
                .publishedAt(v.getPublishedAt())
                .build();
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
