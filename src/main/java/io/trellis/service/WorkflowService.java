package io.trellis.service;

import io.trellis.dto.*;
import io.trellis.entity.WorkflowEntity;
import io.trellis.entity.WorkflowVersionEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.WorkflowRepository;
import io.trellis.repository.WorkflowVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowVersionRepository workflowVersionRepository;
    private final WebhookService webhookService;

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

    public List<WorkflowResponse> listWorkflowsByProject(String projectId) {
        return workflowRepository.findByProjectId(projectId).stream()
                .filter(w -> !w.isArchived())
                .map(this::toResponse)
                .toList();
    }

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

        WorkflowVersionEntity version = WorkflowVersionEntity.builder()
                .workflowId(id)
                .versionNumber(newVersion)
                .versionName(versionName)
                .description(request.getDescription())
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .publishedAt(Instant.now())
                .build();
        workflowVersionRepository.save(version);

        entity.setCurrentVersion(newVersion);
        entity.setPublished(true);
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

    public WorkflowEntity findById(String id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        return WorkflowResponse.builder()
                .id(entity.getId())
                .projectId(entity.getProjectId())
                .name(entity.getName())
                .description(entity.getDescription())
                .published(entity.isPublished())
                .archived(entity.isArchived())
                .currentVersion(entity.getCurrentVersion())
                .nodes(entity.getNodes())
                .connections(entity.getConnections())
                .settings(entity.getSettings())
                .staticData(entity.getStaticData())
                .pinData(entity.getPinData())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
