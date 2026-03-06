package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "credential_shares",
    uniqueConstraints = @UniqueConstraint(columnNames = {"credential_id", "target_project_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialShareEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "credential_id", nullable = false)
    private String credentialId;

    @Column(name = "target_project_id", nullable = false)
    private String targetProjectId;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
