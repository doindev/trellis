package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "credentials")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialEntity {

    @Id
    @NanoId
    private String id;

    private String projectId;

    @Column(name = "config_id")
    private String configId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String data;

    @Column(name = "source_placeholder", columnDefinition = "TEXT")
    private String sourcePlaceholder;

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
