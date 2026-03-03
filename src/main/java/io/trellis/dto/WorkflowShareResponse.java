package io.trellis.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class WorkflowShareResponse {
    private String id;
    private String workflowId;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String permission;
    private Instant createdAt;
}
