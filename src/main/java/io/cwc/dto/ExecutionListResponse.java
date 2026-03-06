package io.cwc.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class ExecutionListResponse {
    private String id;
    private String workflowId;
    private String workflowName;
    private String status;
    private String mode;
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;
}
