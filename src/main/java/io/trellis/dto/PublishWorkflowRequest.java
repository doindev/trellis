package io.trellis.dto;

import lombok.Data;

@Data
public class PublishWorkflowRequest {
    private String versionName;
    private String description;
}
