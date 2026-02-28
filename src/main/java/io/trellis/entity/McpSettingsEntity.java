package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "mcp_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpSettingsEntity {

    @Id
    @NanoId
    private String id;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean agentToolsEnabled = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean agentToolsDedicated = true;

    @Builder.Default
    @Column(nullable = false)
    private String agentToolsPath = "agent";

    @Builder.Default
    @Column(nullable = false)
    private String agentToolsTransport = "STREAMABLE_HTTP";

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
