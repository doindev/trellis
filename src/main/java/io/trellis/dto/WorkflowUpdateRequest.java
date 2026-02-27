package io.trellis.dto;

import lombok.Data;

@Data
public class WorkflowUpdateRequest {
    private String name;
    private String description;
    private Object nodes;
    private Object connections;
    private Object settings;
    private Object staticData;
    private Object pinData;
    private Boolean mcpEnabled;
    private String mcpDescription;
    private Object mcpInputSchema;
    private Object mcpOutputSchema;
}
