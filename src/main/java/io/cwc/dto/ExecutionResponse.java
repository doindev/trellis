package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ExecutionResponse {
    private String id;
    private String workflowId;
    private Object workflowData;
    private String status;
    private String mode;
    private Object resultData;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
}
