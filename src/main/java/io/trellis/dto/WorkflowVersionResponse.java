package io.trellis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class WorkflowVersionResponse {
    private String id;
    private String workflowId;
    private int versionNumber;
    private String versionName;
    private String description;
    private boolean published;
    private Instant publishedAt;
    private Object nodes;
    private Object connections;
    private Object settings;
}
