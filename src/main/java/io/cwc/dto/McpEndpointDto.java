package io.cwc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpEndpointDto {
    private String id;
    private String name;
    private String transport;
    private String path;
    private String url;
    private boolean enabled;
}
