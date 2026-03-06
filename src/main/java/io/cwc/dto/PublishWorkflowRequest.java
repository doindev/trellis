package io.cwc.dto;

import lombok.Data;

@Data
public class PublishWorkflowRequest {
    private String versionName;
    private String description;
    private boolean includePinData;
}
