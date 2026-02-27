package io.trellis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpClientSession {
    private String sessionId;
    private String endpointId;
    private String endpointName;
    private String transport;
    private String clientName;
    private String clientVersion;
    private Instant connectedAt;
    private Instant lastSeenAt;
}
