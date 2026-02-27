package io.trellis.service;

import io.trellis.dto.*;
import io.trellis.entity.TagEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.entity.WorkflowVersionEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.TagRepository;
import io.trellis.repository.WorkflowRepository;
import io.trellis.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WebhookService webhookService;
    private final TagRepository tagRepository;

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

        if (entity.isPublished() && (request.getNodes() != null || request.getConnections() != null)) {
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
                .tags(tagResponses)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
