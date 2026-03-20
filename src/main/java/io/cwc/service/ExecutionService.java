package io.cwc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.ExecutionListResponse;
import io.cwc.dto.ExecutionResponse;
import io.cwc.entity.ExecutionEntity;
import io.cwc.entity.ExecutionEntity.ExecutionMode;
import io.cwc.entity.ExecutionEntity.ExecutionStatus;
import io.cwc.exception.NotFoundException;
import io.cwc.repository.ExecutionRepository;
import io.cwc.util.ExecutionDataObfuscator;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;

    @Transactional
    public ExecutionEntity createExecution(String workflowId, Object workflowData, ExecutionMode mode) {
        ExecutionEntity entity = ExecutionEntity.builder()
                .workflowId(workflowId)
                .workflowData(workflowData)
                .mode(mode)
                .status(ExecutionStatus.NEW)
                .startedAt(Instant.now())
                .build();
        return executionRepository.save(entity);
    }

    @Transactional
    public void updateStatus(String executionId, ExecutionStatus status) {
        ExecutionEntity entity = findEntityById(executionId);
        entity.setStatus(status);
        if (status == ExecutionStatus.RUNNING) {
            entity.setStartedAt(Instant.now());
        }
        executionRepository.save(entity);
    }

    @Transactional
    public void saveResult(String executionId, Object resultData) {
        ExecutionEntity entity = findEntityById(executionId);
        entity.setResultData(ExecutionDataObfuscator.obfuscate(resultData));
        executionRepository.save(entity);
    }

    @Transactional
    public void finish(String executionId, ExecutionStatus status, Object resultData, String errorMessage) {
        ExecutionEntity entity = findEntityById(executionId);
        entity.setStatus(status);
        entity.setResultData(ExecutionDataObfuscator.obfuscate(resultData));
        entity.setFinishedAt(Instant.now());
        entity.setErrorMessage(errorMessage);
        executionRepository.save(entity);
    }

    @Transactional
    public void stop(String executionId) {
        ExecutionEntity entity = findEntityById(executionId);
        entity.setStatus(ExecutionStatus.CANCELED);
        entity.setFinishedAt(Instant.now());
        executionRepository.save(entity);
    }

    public ExecutionResponse getExecution(String id) {
        return toResponse(findEntityById(id));
    }

    public Page<ExecutionListResponse> listExecutions(String workflowId, String projectId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));

        Page<ExecutionEntity> entities;
        if (workflowId != null && status != null) {
            entities = executionRepository.findByWorkflowIdAndStatus(
                    workflowId, ExecutionStatus.valueOf(status), pageable);
        } else if (workflowId != null) {
            entities = executionRepository.findByWorkflowId(workflowId, pageable);
        } else if (projectId != null && status != null) {
            entities = executionRepository.findByProjectIdAndStatus(
                    projectId, ExecutionStatus.valueOf(status), pageable);
        } else if (projectId != null) {
            entities = executionRepository.findByProjectId(projectId, pageable);
        } else if (status != null) {
            entities = executionRepository.findByStatus(ExecutionStatus.valueOf(status), pageable);
        } else {
            entities = executionRepository.findAll(pageable);
        }

        return entities.map(this::toListResponse);
    }

    public Page<ExecutionListResponse> listExecutionsByProjects(List<String> projectIds, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ExecutionEntity> entities;
        if (status != null) {
            entities = executionRepository.findByProjectIdsAndStatus(projectIds, ExecutionStatus.valueOf(status), pageable);
        } else {
            entities = executionRepository.findByProjectIds(projectIds, pageable);
        }
        return entities.map(this::toListResponse);
    }

    /**
     * List executions for metrics: excludes MANUAL mode, only published workflows,
     * filtered by project(s).
     */
    public Page<ExecutionListResponse> listProductionExecutions(String projectId, List<String> projectIds, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));
        Page<ExecutionEntity> entities;
        if (projectId != null) {
            entities = executionRepository.findProductionByProjectId(projectId, ExecutionMode.MANUAL, pageable);
        } else if (projectIds != null && !projectIds.isEmpty()) {
            entities = executionRepository.findProductionByProjectIds(projectIds, ExecutionMode.MANUAL, pageable);
        } else {
            entities = Page.empty(pageable);
        }
        return entities.map(this::toListResponse);
    }

    @Transactional
    public void deleteExecution(String id) {
        ExecutionEntity entity = findEntityById(id);
        executionRepository.delete(entity);
    }

    private ExecutionEntity findEntityById(String id) {
        return executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution not found: " + id));
    }

    private ExecutionResponse toResponse(ExecutionEntity entity) {
        return ExecutionResponse.builder()
                .id(entity.getId())
                .workflowId(entity.getWorkflowId())
                .workflowData(entity.getWorkflowData())
                .status(entity.getStatus().name().toLowerCase())
                .mode(entity.getMode().name().toLowerCase())
                .resultData(entity.getResultData())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private ExecutionListResponse toListResponse(ExecutionEntity entity) {
        return ExecutionListResponse.builder()
                .id(entity.getId())
                .workflowId(entity.getWorkflowId())
                .status(entity.getStatus().name().toLowerCase())
                .mode(entity.getMode().name().toLowerCase())
                .startedAt(entity.getStartedAt())
                .finishedAt(entity.getFinishedAt())
                .errorMessage(entity.getErrorMessage())
                .build();
    }
}
