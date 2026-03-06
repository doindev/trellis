package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "tags")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

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
