package io.cwc.dto;

import lombok.Data;

@Data
public class VariableRequest {
    private String key;
    private String value;
    private String type;
    private String projectId;
}
