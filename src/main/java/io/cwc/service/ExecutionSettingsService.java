package io.cwc.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.cwc.dto.ExecutionSettingsDto;
import io.cwc.entity.ExecutionSettingsEntity;
import io.cwc.repository.ExecutionSettingsRepository;

@Service
@RequiredArgsConstructor
public class ExecutionSettingsService {

    private final ExecutionSettingsRepository repository;

    public ExecutionSettingsDto getSettings() {
        return repository.findFirstByOrderByCreatedAtAsc()
                .map(e -> ExecutionSettingsDto.builder()
                        .saveExecutionProgress(e.getSaveExecutionProgress())
                        .saveManualExecutions(e.getSaveManualExecutions())
                        .executionTimeout(e.getExecutionTimeout())
                        .errorWorkflow(e.getErrorWorkflow())
                        .build())
                .orElse(ExecutionSettingsDto.builder()
                        .saveExecutionProgress("yes")
                        .saveManualExecutions("yes")
                        .executionTimeout(-1)
                        .build());
    }

    @Transactional
    public ExecutionSettingsDto updateSettings(ExecutionSettingsDto dto) {
        ExecutionSettingsEntity entity = repository.findFirstByOrderByCreatedAtAsc()
                .orElseGet(ExecutionSettingsEntity::new);
        if (dto.getSaveExecutionProgress() != null) entity.setSaveExecutionProgress(dto.getSaveExecutionProgress());
        if (dto.getSaveManualExecutions() != null) entity.setSaveManualExecutions(dto.getSaveManualExecutions());
        entity.setExecutionTimeout(dto.getExecutionTimeout());
        entity.setErrorWorkflow(dto.getErrorWorkflow());
        repository.save(entity);
        return getSettings();
    }
}
