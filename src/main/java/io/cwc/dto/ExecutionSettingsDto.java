package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionSettingsDto {
    private String saveExecutionProgress;
    private String saveManualExecutions;
    private int executionTimeout;
    private String errorWorkflow;
}
