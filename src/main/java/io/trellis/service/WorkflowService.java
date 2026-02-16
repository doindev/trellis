package io.trellis.service;

import io.trellis.dto.WorkflowCreateRequest;
import io.trellis.dto.WorkflowResponse;
import io.trellis.dto.WorkflowUpdateRequest;
import io.trellis.entity.WorkflowEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WebhookService webhookService;

    public List<WorkflowResponse> listWorkflows() {
        return workflowRepository.findAll().stream()
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
        if (entity.isActive()) {
            deactivateWorkflow(id);
        }
        workflowRepository.delete(entity);
    }

    @Transactional
    public WorkflowResponse activateWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        entity.setActive(true);
        entity = workflowRepository.save(entity);
        webhookService.registerWorkflowWebhooks(entity);
        log.info("Activated workflow: {} ({})", entity.getName(), id);
        return toResponse(entity);
    }

    @Transactional
    public WorkflowResponse deactivateWorkflow(String id) {
        WorkflowEntity entity = findById(id);
        entity.setActive(false);
        entity = workflowRepository.save(entity);
        webhookService.deregisterWorkflowWebhooks(id);
        log.info("Deactivated workflow: {} ({})", entity.getName(), id);
        return toResponse(entity);
    }

    public List<WorkflowResponse> getActiveWorkflows() {
        return workflowRepository.findByActive(true).stream()
                .map(this::toResponse)
                .toList();
    }

    public WorkflowEntity findById(String id) {
        return workflowRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + id));
    }

    private WorkflowResponse toResponse(WorkflowEntity entity) {
        return WorkflowResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .active(entity.isActive())
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
