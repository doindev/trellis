package io.trellis.entity;

import io.trellis.util.NanoId;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "chat_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String title;

    private String agentId;

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
