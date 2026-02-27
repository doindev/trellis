package io.trellis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class WorkflowResponse {
    private String id;
    private String projectId;
    private String name;
    private String description;
    private boolean published;
    private boolean archived;
    private int currentVersion;
    private boolean versionIsDirty;
    private Object nodes;
    private Object connections;
    private Object settings;
    private Object staticData;
    private Object pinData;
    private boolean mcpEnabled;
    private String mcpDescription;
    private Object mcpInputSchema;
    private Object mcpOutputSchema;
    private List<TagResponse> tags;
    private Instant createdAt;
    private Instant updatedAt;
}
