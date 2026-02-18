package io.trellis.dto;

import lombok.Data;

@Data
public class WorkflowCreateRequest {
    private String name;
    private String description;
    private Object nodes;
    private Object connections;
    private Object settings;
}
