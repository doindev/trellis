package io.trellis.dto;

import lombok.Data;

@Data
public class WorkflowShareRequest {
    private String userId;
    private String permission;
}
