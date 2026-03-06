package io.cwc.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import io.cwc.util.NanoId;

@Entity
@Table(name = "chat_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEntity {

    @Id
    @NanoId
    private String id;

    @Column(nullable = false)
    private String sessionId;

    @Column(name = "msg_role", nullable = false)
    private String role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
