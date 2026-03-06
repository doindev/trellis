package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "chat_agents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAgentEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(columnDefinition = "TEXT")
    private String systemPrompt;

    private String icon;

    private String model;

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
