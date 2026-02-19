package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "ai_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSettingsEntity {

    @Id
    @NanoId
    private String id;

    @Builder.Default
    @Column(nullable = false)
    private String provider = "openai";

    @Column(name = "api_key")
    private String apiKey;

    @Builder.Default
    @Column(nullable = false)
    private String model = "gpt-4o-mini";

    private String baseUrl;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

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
