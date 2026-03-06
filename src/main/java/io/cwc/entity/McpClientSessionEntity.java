package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "mcp_client_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpClientSessionEntity {

    @Id
    private String sessionId;

    @Column(nullable = false)
    private String instanceId;

    @Column(nullable = false)
    private String endpointId;

    @Column(nullable = false)
    private String endpointName;

    @Column(nullable = false)
    private String transport;

    private String clientName;

    private String clientVersion;

    @Builder.Default
    @Column(nullable = false)
    private Instant connectedAt = Instant.now();

    private Instant disconnectedAt;

    @Builder.Default
    @Column(nullable = false)
    private Instant lastSeenAt = Instant.now();
}
