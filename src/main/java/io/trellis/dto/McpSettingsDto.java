package io.trellis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSettingsDto {
    private boolean enabled;
    private boolean agentToolsEnabled;
    private boolean agentToolsDedicated;
    private String agentToolsPath;
    private String agentToolsTransport;
    private String agentToolsUrl;
    private List<McpEndpointDto> endpoints;
}
