package io.cwc.dto;

import lombok.Data;

@Data
public class ProjectMcpRequest {
    private boolean enabled;
    private String path;
    private String transport;
}
