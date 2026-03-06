package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "swagger_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwaggerSettingsEntity {

    @Id
    @NanoId
    private String id;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    private String apiTitle;

    @Column(columnDefinition = "TEXT")
    private String apiDescription;

    private String apiVersion;

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
