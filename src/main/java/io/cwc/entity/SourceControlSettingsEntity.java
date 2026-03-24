package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "source_control_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceControlSettingsEntity {

    @Id
    @NanoId
    private String id;

    @Builder.Default
    @Column(nullable = false)
    private String provider = "github";

    private String repoUrl;

    @Builder.Default
    private String branch = "main";

    private String token;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    private Instant lastSyncAt;

    private String lastSyncStatus;

    @Column(columnDefinition = "TEXT")
    private String lastSyncError;

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
