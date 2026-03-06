package io.cwc.dto;

import lombok.Data;

@Data
public class WorkflowCreateRequest {
    private String projectId;
    private String name;
    private String description;
    private Object nodes;
    private Object connections;
    private Object settings;
}
