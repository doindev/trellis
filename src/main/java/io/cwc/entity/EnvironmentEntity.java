package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "environments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String gitBranch;

    private String description;

    @Builder.Default
    @Column(nullable = false)
    private int sortOrder = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean isCurrent = false;

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
