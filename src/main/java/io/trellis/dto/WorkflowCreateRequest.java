package io.trellis.dto;

import lombok.Data;

@Data
public class WorkflowCreateRequest {
    private String name;
    private Object nodes;
    private Object connections;
    private Object settings;
}
