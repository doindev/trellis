package io.trellis.service;

import io.trellis.dto.McpSettingsDto;
import io.trellis.entity.McpSettingsEntity;
import io.trellis.entity.WorkflowEntity;
import io.trellis.exception.NotFoundException;
import io.trellis.repository.McpSettingsRepository;
import io.trellis.repository.WorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpSettingsService {

    private final McpSettingsRepository repository;
    private final WorkflowRepository workflowRepository;

    @Value("${server.port:5678}")
    private int serverPort;

    public McpSettingsDto getSettings() {
        boolean enabled = repository.findFirstByOrderByCreatedAtAsc()
                .map(McpSettingsEntity::isEnabled)
                .orElse(false);
        return McpSettingsDto.builder()
                .enabled(enabled)
                .sseUrl("http://localhost:" + serverPort + "/sse")
                .build();
    }

    @Transactional
    public McpSettingsDto setEnabled(boolean enabled) {
        McpSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(McpSettingsEntity::new);
        entity.setEnabled(enabled);
        repository.save(entity);
        return McpSettingsDto.builder()
                .enabled(entity.isEnabled())
                .sseUrl("http://localhost:" + serverPort + "/sse")
                .build();
    }

    public List<WorkflowEntity> getEnabledWorkflows() {
        return workflowRepository.findByMcpEnabledTrue();
    }

    @Transactional
    public void setWorkflowMcpEnabled(String workflowId, boolean enabled) {
        WorkflowEntity workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found: " + workflowId));
        workflow.setMcpEnabled(enabled);
        workflowRepository.save(workflow);
    }
}
