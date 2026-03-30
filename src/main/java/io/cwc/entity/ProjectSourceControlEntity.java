package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

/**
 * Per-project, per-user git source control configuration.
 * Each user stores their own token — only they can use it for sync/push.
 */
@Entity
@Table(name = "project_source_control",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectSourceControlEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String repoUrl;

    @Builder.Default
    @Column(nullable = false)
    private String branch = "main";

    @Column(columnDefinition = "TEXT")
    private String token;  // encrypted via CredentialEncryptionService

    @Builder.Default
    @Column(nullable = false)
    private String provider = "github";

    private String repoSubPath;

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
