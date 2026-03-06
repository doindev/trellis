package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "mcp_endpoints")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpEndpointEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String transport;

    @Column(nullable = false, unique = true)
    private String path;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

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
