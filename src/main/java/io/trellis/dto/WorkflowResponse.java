package io.trellis.dto;

import lombok.Builder;
import lombok.Data;
import java.time.Instant;

@Data
@Builder
public class WorkflowResponse {
    private String id;
    private String name;
    private String description;
    private boolean published;
    private boolean archived;
    private int currentVersion;
    private Object nodes;
    private Object connections;
    private Object settings;
    private Object staticData;
    private Object pinData;
    private Instant createdAt;
    private Instant updatedAt;
}
