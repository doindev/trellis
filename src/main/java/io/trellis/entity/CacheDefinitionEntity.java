package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "cache_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheDefinitionEntity {

    @Id
    @NanoId
    private String id;

    @Column(name = "cache_name", nullable = false, unique = true)
    private String name;

    private String description;

    @Builder.Default
    private int maxSize = 1000;

    @Builder.Default
    private long ttlSeconds = 3600;

    private String projectId;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
