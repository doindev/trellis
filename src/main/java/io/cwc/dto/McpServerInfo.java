package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpServerInfo {
    private String id;
    private String name;
    private String projectId;
    private List<McpEndpointDto> endpoints;
    private int connectedClients;
}
