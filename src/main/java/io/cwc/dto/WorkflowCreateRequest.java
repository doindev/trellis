package io.cwc.dto;

import lombok.Data;

@Data
public class WorkflowCreateRequest {
    private String projectId;
    private String name;
    private String description;
    private String type;
    private String icon;
    private Object nodes;
    private Object connections;
    private Object settings;
    private Boolean mcpEnabled;
    private String mcpDescription;
    private Object mcpInputSchema;
    private Object mcpOutputSchema;
    private Boolean swaggerEnabled;
}
