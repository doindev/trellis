package io.trellis.dto;

import lombok.Data;

@Data
public class WorkflowUpdateRequest {
    private String name;
    private Object nodes;
    private Object connections;
    private Object settings;
    private Object staticData;
    private Object pinData;
}
